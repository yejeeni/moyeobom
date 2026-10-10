package com.moyeobom.mate.service;

import com.moyeobom.common.config.SchedulingConfig;
import com.moyeobom.mate.service.MateFactory.PlacedMate;
import com.moyeobom.room.domain.Occupant;
import com.moyeobom.room.domain.Room;
import com.moyeobom.room.domain.Seat;
import com.moyeobom.room.domain.SeatState;
import com.moyeobom.room.dto.RoomDtos.CountsPayload;
import com.moyeobom.room.dto.RoomDtos.OccupantView;
import com.moyeobom.room.dto.RoomDtos.RoomEventType;
import com.moyeobom.room.dto.RoomDtos.StatePayload;
import com.moyeobom.room.service.RoomMessenger;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

/**
 * 가상 메이트를 움직인다. 매초 확인하지 않고, 상태가 바뀔 때 다음 전이 시각을 계산해 한 번만 예약한다.
 * <p>
 * 전이 순서: 방 락을 잡는다 → 규칙에 따라 다음 상태를 정한다 → 이벤트를 발행한다 → 다음 전이를 예약한다 → 락을 푼다.
 */
@Slf4j
@Component
public class MateScheduler {

    private final MateFactory mateFactory;
    private final RoomMessenger messenger;
    private final TaskScheduler taskScheduler;
    private final Clock clock;

    public MateScheduler(MateFactory mateFactory, RoomMessenger messenger,
                         @Qualifier(SchedulingConfig.TASK_SCHEDULER) TaskScheduler taskScheduler, Clock clock) {
        this.mateFactory = mateFactory;
        this.messenger = messenger;
        this.taskScheduler = taskScheduler;
        this.clock = clock;
    }

    /**
     * 방 락을 잡은 상태에서 부른다. 무작위 수의 메이트를 무작위 자리에 앉히고,
     * 남은 빈자리에는 시간차를 두고 새 메이트가 들어오도록 예약한다.
     */
    public void populate(Room room, Instant now) {
        List<Seat> seats = new ArrayList<>(room.virtualSeats());
        mateFactory.shuffle(seats);
        int count = mateFactory.initialMateCount(seats.size());
        List<PlacedMate> mates = mateFactory.createInitial(count, now);
        for (int i = 0; i < seats.size(); i++) {
            Seat seat = seats.get(i);
            if (i < count) {
                seat(room, seat, mates.get(i));
            } else {
                scheduleRefill(room, seat, now.plus(mateFactory.arrivalDelay()));
            }
        }
    }

    /** 방 락을 잡은 상태에서 부른다. 메이트를 앉히고 첫 전이를 예약한다. */
    public void seat(Room room, Seat seat, PlacedMate mate) {
        seat.sit(mate.occupant());
        scheduleTransition(room, seat, mate.occupant(), mate.nextTransitionAt());
    }

    void transition(Room room, int seatNo, Occupant expected) {
        room.withLock(() -> {
            Seat seat = room.seat(seatNo);
            // 방이 정리됐거나 그 사이 자리 주인이 바뀌었으면 늦게 실행된 예약이다
            if (room.isClosed() || seat.occupant().filter(occupant -> occupant == expected).isEmpty()) {
                return;
            }
            Instant now = clock.instant();
            if (expected.getState() == SeatState.FOCUS) {
                finishFocus(room, seat, expected, now);
            } else {
                finishBreak(room, seat, expected, now);
            }
        });
    }

    void refill(Room room, int seatNo) {
        room.withLock(() -> {
            Seat seat = room.seat(seatNo);
            if (room.isClosed() || !seat.isEmpty()) {
                return;
            }
            PlacedMate newcomer = mateFactory.createNewcomer(clock.instant());
            seat(room, seat, newcomer);
            messenger.toRoom(room, RoomEventType.SEAT_JOINED, seatNo, OccupantView.of(newcomer.occupant()));
        });
    }

    private void finishFocus(Room room, Seat seat, Occupant mate, Instant now) {
        // 과반 집중 규칙: 이 메이트가 쉬면 집중 인원이 앉은 사람의 과반보다 적어질 때는 휴식 대신 집중을 늘린다
        long focusingAfterBreak = room.focusingCount() - 1;
        if (focusingAfterBreak < MateFactory.majorityOf(room.occupiedCount())) {
            scheduleTransition(room, seat, mate, now.plus(mateFactory.focusExtension()));
            return;
        }
        if (mateFactory.rollComplete()) {
            int remaining = mate.getRemainingCount() > 1 ? mate.getRemainingCount() - 1 : mateFactory.newRemainingCount();
            mate.changeCounts(mate.getCompletedCount() + 1, remaining);
            messenger.toRoom(room, RoomEventType.COUNTS_CHANGED, seat.getSeatNo(),
                    new CountsPayload(mate.getCompletedCount(), mate.getRemainingCount()));
        }
        mate.changeState(SeatState.BREAK, now);
        messenger.toRoom(room, RoomEventType.STATE_CHANGED, seat.getSeatNo(), new StatePayload(SeatState.BREAK, now));
        scheduleTransition(room, seat, mate, now.plus(mateFactory.breakDuration()));
    }

    private void finishBreak(Room room, Seat seat, Occupant mate, Instant now) {
        if (mateFactory.rollLeave()) {
            seat.leave();
            messenger.toRoom(room, RoomEventType.SEAT_LEFT, seat.getSeatNo(), null);
            scheduleRefill(room, seat, now.plus(mateFactory.refillDelay()));
            return;
        }
        mate.changeState(SeatState.FOCUS, now);
        messenger.toRoom(room, RoomEventType.STATE_CHANGED, seat.getSeatNo(), new StatePayload(SeatState.FOCUS, now));
        scheduleTransition(room, seat, mate, now.plus(mateFactory.focusDuration()));
    }

    private void scheduleRefill(Room room, Seat seat, Instant at) {
        int seatNo = seat.getSeatNo();
        seat.reserve(taskScheduler.schedule(() -> refill(room, seatNo), at), at);
    }

    private void scheduleTransition(Room room, Seat seat, Occupant mate, Instant at) {
        int seatNo = seat.getSeatNo();
        seat.reserve(taskScheduler.schedule(() -> transition(room, seatNo, mate), at), at);
    }
}
