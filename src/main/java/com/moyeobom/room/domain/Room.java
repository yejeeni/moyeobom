package com.moyeobom.room.domain;

import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import lombok.Getter;

/**
 * 1단계 열람실. 게스트 1명당 방 1개이며 서버 메모리에만 있다.
 * 스케줄러 스레드와 요청 스레드가 함께 바꾸므로 모든 변경은 {@link #withLock}으로 한다.
 */
public class Room {

    /** 한 방의 최대 자리 수. 인원은 1~9명 중에서 고른다. */
    public static final int MAX_SEAT_COUNT = 9;

    @Getter
    private final String roomId;
    @Getter
    private final Long ownerGuestId;
    @Getter
    private final int seatCount;
    @Getter
    private final int mySeatNo;
    private final List<Seat> seats;
    private final ReentrantLock lock = new ReentrantLock();
    @Getter
    private RoomStatus status = RoomStatus.GRACE;
    private ScheduledFuture<?> graceExpiry;
    @Getter
    private boolean closed;

    public Room(String roomId, Long ownerGuestId, int seatCount, int mySeatNo) {
        if (seatCount < 1 || seatCount > MAX_SEAT_COUNT) {
            throw new IllegalArgumentException("인원은 1~9명입니다: " + seatCount);
        }
        if (mySeatNo < 1 || mySeatNo > seatCount) {
            throw new IllegalArgumentException("자리 번호는 1~" + seatCount + "입니다: " + mySeatNo);
        }
        this.roomId = roomId;
        this.ownerGuestId = ownerGuestId;
        this.seatCount = seatCount;
        this.mySeatNo = mySeatNo;
        this.seats = IntStream.rangeClosed(1, seatCount).mapToObj(Seat::new).toList();
    }

    /** 9명짜리 방 */
    public Room(String roomId, Long ownerGuestId, int mySeatNo) {
        this(roomId, ownerGuestId, MAX_SEAT_COUNT, mySeatNo);
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

    public Seat mySeat() {
        return seat(mySeatNo);
    }

    public List<Seat> mateSeats() {
        return seats.stream().filter(seat -> seat.getSeatNo() != mySeatNo).toList();
    }

    /** 자리에 앉은 사람 수(나 포함) */
    public long occupiedCount() {
        return seats.stream().filter(seat -> !seat.isEmpty()).count();
    }

    public long focusingCount() {
        return seats.stream()
                .filter(seat -> seat.occupant().map(Occupant::isFocusing).orElse(false))
                .count();
    }

    /** WebSocket이 연결되면 활성 상태가 되고 유예 만료 예약을 취소한다. */
    public void activate() {
        this.status = RoomStatus.ACTIVE;
        if (graceExpiry != null) {
            graceExpiry.cancel(false);
            graceExpiry = null;
        }
    }

    /** 연결이 끊기면 정해진 시간 뒤 정리되도록 유예 상태가 된다. */
    public void startGrace(ScheduledFuture<?> expiry) {
        this.status = RoomStatus.GRACE;
        if (graceExpiry != null) {
            graceExpiry.cancel(false);
        }
        this.graceExpiry = expiry;
    }

    /** 모든 예약을 취소한다. 닫힌 방에서 늦게 실행된 예약 작업은 아무것도 하지 않는다. */
    public void close() {
        this.closed = true;
        if (graceExpiry != null) {
            graceExpiry.cancel(false);
            graceExpiry = null;
        }
        seats.forEach(Seat::cancelReservation);
    }
}
