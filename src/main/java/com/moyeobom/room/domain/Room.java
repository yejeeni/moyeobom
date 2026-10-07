package com.moyeobom.room.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;
import java.util.stream.IntStream;
import lombok.Getter;

/**
 * 열람실. 코드를 아는 사람은 누구나 실제 자리에 들어올 수 있고, 가상 자리는 메이트가 채운다. 서버 메모리에만 있다.
 * 스케줄러 스레드와 여러 사람의 요청 스레드가 함께 바꾸므로 모든 변경은 {@link #withLock}으로 한다.
 */
public class Room {

    /** 한 방의 최대 자리 수(실제 + 가상) */
    public static final int MAX_SEAT_COUNT = 9;

    @Getter
    private final String roomId;
    @Getter
    private final String code;
    @Getter
    private final int seatCount;
    @Getter
    private final int realSeatCount;
    private final List<Seat> seats;
    private final ReentrantLock lock = new ReentrantLock();
    /** 참여자 → 자리 번호. 구독 검사처럼 락 밖에서도 읽으므로 동시성 맵을 쓴다 */
    private final Map<Long, Integer> seatByMember = new ConcurrentHashMap<>();
    /** 연결이 끊긴 참여자의 자리를 비우기까지의 예약 */
    private final Map<Long, ScheduledFuture<?>> memberGrace = new HashMap<>();
    @Getter
    private boolean closed;

    private Room(String roomId, String code, List<Seat> seats) {
        this.roomId = roomId;
        this.code = code;
        this.seats = seats;
        this.seatCount = seats.size();
        this.realSeatCount = (int) seats.stream().filter(Seat::isReal).count();
    }

    /**
     * 실제 자리와 가상 자리를 무작위 위치에 섞어 방을 만든다.
     */
    public static Room create(String roomId, String code, int virtualSeats, int realSeats, RandomGenerator random) {
        int total = virtualSeats + realSeats;
        if (realSeats < 1 || virtualSeats < 0 || total > MAX_SEAT_COUNT) {
            throw new IllegalArgumentException("자리는 실제 1명 이상, 합계 1~9명입니다: 가상 " + virtualSeats + ", 실제 " + realSeats);
        }
        List<SeatType> types = new ArrayList<>();
        IntStream.range(0, realSeats).forEach(i -> types.add(SeatType.REAL));
        IntStream.range(0, virtualSeats).forEach(i -> types.add(SeatType.VIRTUAL));
        Collections.shuffle(types, random);
        List<Seat> seats = IntStream.range(0, total).mapToObj(i -> new Seat(i + 1, types.get(i))).toList();
        return new Room(roomId, code, seats);
    }

    /** 실제 자리가 mySeatNo 하나뿐이고 나머지는 가상인 방. 테스트와 혼자 쓰는 방에 쓴다. */
    public static Room solo(String roomId, int seatCount, int mySeatNo) {
        if (seatCount < 1 || seatCount > MAX_SEAT_COUNT || mySeatNo < 1 || mySeatNo > seatCount) {
            throw new IllegalArgumentException("자리 번호가 올바르지 않습니다: " + mySeatNo + "/" + seatCount);
        }
        List<Seat> seats = IntStream.rangeClosed(1, seatCount)
                .mapToObj(no -> new Seat(no, no == mySeatNo ? SeatType.REAL : SeatType.VIRTUAL))
                .toList();
        return new Room(roomId, "SOLO" + roomId, seats);
    }

    public <T> T withLock(Supplier<T> action) {
        lock.lock();
        try {
            return action.get();
        } finally {
            lock.unlock();
        }
    }

    public void withLock(Runnable action) {
        withLock(() -> {
            action.run();
            return null;
        });
    }

    public List<Seat> seats() {
        return seats;
    }

    public Seat seat(int seatNo) {
        return seats.get(seatNo - 1);
    }

    /** 가상 메이트가 앉는 자리들 */
    public List<Seat> virtualSeats() {
        return seats.stream().filter(seat -> !seat.isReal()).toList();
    }

    /** 비어 있는 실제 자리들 */
    public List<Seat> waitingSeats() {
        return seats.stream().filter(Seat::isWaiting).toList();
    }

    public boolean hasMember(Long guestId) {
        return seatByMember.containsKey(guestId);
    }

    public Set<Long> members() {
        return Set.copyOf(seatByMember.keySet());
    }

    public Optional<Seat> seatOf(Long guestId) {
        return Optional.ofNullable(seatByMember.get(guestId)).map(this::seat);
    }

    /** 실제 자리에 참여자를 앉힌다(락 안에서) */
    public void addMember(Long guestId, Seat seat, Occupant occupant) {
        if (!seat.isWaiting()) {
            throw new IllegalStateException("비어 있는 실제 자리가 아닙니다: " + seat.getSeatNo());
        }
        seat.sit(occupant);
        seatByMember.put(guestId, seat.getSeatNo());
    }

    /** 참여자를 내보내고 그 자리를 비운다(락 안에서). 비운 자리를 돌려준다 */
    public Optional<Seat> removeMember(Long guestId) {
        cancelGrace(guestId);
        Integer seatNo = seatByMember.remove(guestId);
        if (seatNo == null) {
            return Optional.empty();
        }
        Seat seat = seat(seatNo);
        seat.leave();
        return Optional.of(seat);
    }

    /** 자리에 앉은 사람 수(실제 + 가상) */
    public long occupiedCount() {
        return seats.stream().filter(seat -> !seat.isEmpty()).count();
    }

    public long focusingCount() {
        return seats.stream()
                .filter(seat -> seat.occupant().map(Occupant::isFocusing).orElse(false))
                .count();
    }

    /** 연결이 없는 참여자의 자리를 정해진 시간 뒤 비우도록 예약한다(락 안에서) */
    public void startGrace(Long guestId, ScheduledFuture<?> expiry) {
        cancelGrace(guestId);
        memberGrace.put(guestId, expiry);
    }

    /** 다시 연결되면 자리 비우기 예약을 취소한다(락 안에서) */
    public void cancelGrace(Long guestId) {
        ScheduledFuture<?> previous = memberGrace.remove(guestId);
        if (previous != null) {
            previous.cancel(false);
        }
    }

    public boolean isInGrace(Long guestId) {
        return memberGrace.containsKey(guestId);
    }

    /** 모든 예약을 취소한다. 닫힌 방에서 늦게 실행된 예약 작업은 아무것도 하지 않는다. */
    public void close() {
        this.closed = true;
        memberGrace.values().forEach(future -> future.cancel(false));
        memberGrace.clear();
        seats.forEach(Seat::cancelReservation);
    }
}
