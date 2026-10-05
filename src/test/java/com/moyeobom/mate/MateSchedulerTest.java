package com.moyeobom.mate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.moyeobom.mate.config.MateProperties;
import com.moyeobom.mate.config.MateProperties.DurationRange;
import com.moyeobom.mate.service.MateFactory;
import com.moyeobom.mate.service.MateFactory.PlacedMate;
import com.moyeobom.mate.service.MateScheduler;
import com.moyeobom.room.domain.CharacterParts;
import com.moyeobom.room.domain.Occupant;
import com.moyeobom.room.domain.OccupantKind;
import com.moyeobom.room.domain.Room;
import com.moyeobom.room.domain.Seat;
import com.moyeobom.room.domain.SeatState;
import com.moyeobom.room.dto.RoomDtos.RoomEventType;
import com.moyeobom.room.service.RoomMessenger;
import com.moyeobom.support.FakeTaskScheduler;
import com.moyeobom.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MateSchedulerTest {

    static final Instant START = Instant.parse("2026-10-05T09:00:00Z");
    static final MateProperties PROPERTIES = new MateProperties(
            range(Duration.ofMinutes(25), Duration.ofMinutes(90)),
            range(Duration.ofMinutes(5), Duration.ofMinutes(15)),
            0.4, 0.2,
            range(Duration.ofSeconds(30), Duration.ofMinutes(2)),
            5,
            range(Duration.ofMinutes(5), Duration.ofMinutes(10)),
            new MateProperties.Initial(0.8, 3, 1, 4));

    MutableClock clock;
    FakeTaskScheduler scheduler;
    MateScheduler mateScheduler;
    List<RoomEventType> events;
    Room room;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        scheduler = new FakeTaskScheduler(clock);
        events = new ArrayList<>();
        RoomMessenger messenger = mock(RoomMessenger.class);
        doAnswer(invocation -> events.add(invocation.getArgument(1)))
                .when(messenger).toRoom(any(), any(), any(), any());
        mateScheduler = new MateScheduler(new MateFactory(PROPERTIES, new Random(42)), messenger, scheduler, clock);
        room = createRoom(SeatState.IDLE);
    }

    @Test
    void 처음_입장하면_메이트_8명이_이미_공부하던_중이다() {
        List<Occupant> mates = room.mateSeats().stream().map(seat -> seat.occupant().orElseThrow()).toList();

        assertThat(mates).hasSize(8).allMatch(Occupant::isVirtual);
        assertThat(mates.stream().filter(Occupant::isFocusing).count()).isEqualTo(6);
        assertThat(mates).allSatisfy(mate -> {
            assertThat(mate.getSince()).isBeforeOrEqualTo(START);
            assertThat(mate.getCompletedCount()).isBetween(0, 3);
            assertThat(mate.getRemainingCount()).isBetween(1, 4);
        });
        for (Seat seat : room.mateSeats()) {
            Occupant mate = seat.occupant().orElseThrow();
            Duration total = Duration.between(mate.getSince(), seat.getScheduledAt());
            if (mate.isFocusing()) {
                assertThat(total).isBetween(Duration.ofMinutes(25), Duration.ofMinutes(90));
            } else {
                assertThat(total).isBetween(Duration.ofMinutes(5), Duration.ofMinutes(15));
            }
            assertThat(seat.getScheduledAt()).isAfter(START);
        }
    }

    @Test
    void 하루_동안_돌려도_집중_인원은_항상_5명_이상이고_지속_시간은_범위_안이다() {
        Instant end = START.plus(Duration.ofHours(24));
        int transitions = 0;
        while (scheduler.runNext(end)) {
            transitions++;
            assertThat(room.focusingCount()).isGreaterThanOrEqualTo(5);
            for (Seat seat : room.mateSeats()) {
                assertScheduledWithinRange(seat);
            }
        }

        assertThat(transitions).isGreaterThan(100);
        assertThat(events).contains(RoomEventType.STATE_CHANGED, RoomEventType.COUNTS_CHANGED,
                RoomEventType.SEAT_LEFT, RoomEventType.SEAT_JOINED);
    }

    @Test
    void 퇴장한_자리는_잠시_뒤_새_메이트가_집중_상태로_채운다() {
        Instant end = START.plus(Duration.ofHours(24));
        Seat leftSeat = null;
        while (scheduler.runNext(end)) {
            leftSeat = room.mateSeats().stream().filter(Seat::isEmpty).findFirst().orElse(null);
            if (leftSeat != null) {
                break;
            }
        }
        assertThat(leftSeat).as("하루 안에 한 번은 퇴장한다").isNotNull();
        Instant leftAt = clock.instant();
        Instant refillAt = leftSeat.getScheduledAt();
        assertThat(Duration.between(leftAt, refillAt)).isBetween(Duration.ofSeconds(30), Duration.ofMinutes(2));

        while (leftSeat.isEmpty() && scheduler.runNext(end)) {
            // 채워질 때까지 돌린다
        }

        Occupant newcomer = leftSeat.occupant().orElseThrow();
        assertThat(newcomer.getState()).isEqualTo(SeatState.FOCUS);
        assertThat(newcomer.getSince()).isEqualTo(refillAt);
        assertThat(newcomer.getCompletedCount()).isZero();
    }

    @Test
    void 집중_인원이_기준보다_적어지면_휴식_대신_집중을_연장한다() {
        // 나와 메이트 4명만 집중 중이고 나머지는 휴식 중인 방
        Room small = new Room("r-small", 2L, 1);
        small.mySeat().sit(occupant(OccupantKind.REAL, SeatState.FOCUS));
        List<Seat> mates = small.mateSeats();
        for (int i = 0; i < mates.size(); i++) {
            SeatState state = i < 4 ? SeatState.FOCUS : SeatState.BREAK;
            Instant next = state == SeatState.FOCUS ? START.plus(Duration.ofMinutes(1)) : START.plus(Duration.ofHours(10));
            mateScheduler.seat(small, mates.get(i), new PlacedMate(occupant(OccupantKind.VIRTUAL, state), next));
        }

        scheduler.runNext(START.plus(Duration.ofMinutes(1)));

        // 첫 메이트가 쉬면 4명이 되므로 연장된다
        assertThat(small.focusingCount()).isEqualTo(5);
        Seat extended = mates.getFirst();
        assertThat(extended.occupant().orElseThrow().getState()).isEqualTo(SeatState.FOCUS);
        assertThat(Duration.between(START.plus(Duration.ofMinutes(1)), extended.getScheduledAt()))
                .isBetween(Duration.ofMinutes(5), Duration.ofMinutes(10));
    }

    @Test
    void 정리된_방의_예약은_아무것도_하지_않는다() {
        room.close();

        assertThat(scheduler.pendingCount()).isZero();
        assertThat(scheduler.runNext(START.plus(Duration.ofHours(24)))).isFalse();
        assertThat(events).isEmpty();
    }

    private Room createRoom(SeatState myState) {
        Room created = new Room("r-test", 1L, 5);
        created.mySeat().sit(occupant(OccupantKind.REAL, myState));
        List<Seat> mateSeats = created.mateSeats();
        List<PlacedMate> mates = mateScheduler.createInitialMates(mateSeats.size(), START);
        for (int i = 0; i < mateSeats.size(); i++) {
            mateScheduler.seat(created, mateSeats.get(i), mates.get(i));
        }
        return created;
    }

    private void assertScheduledWithinRange(Seat seat) {
        Instant now = clock.instant();
        if (seat.getScheduledAt() == null || !seat.getScheduledAt().isAfter(now)) {
            return;
        }
        Duration until = Duration.between(now, seat.getScheduledAt());
        if (seat.isEmpty()) {
            assertThat(until).isLessThanOrEqualTo(Duration.ofMinutes(2));
        } else if (seat.occupant().orElseThrow().getState() == SeatState.BREAK) {
            assertThat(until).isLessThanOrEqualTo(Duration.ofMinutes(15));
        } else {
            assertThat(until).isLessThanOrEqualTo(Duration.ofMinutes(90));
        }
    }

    private static Occupant occupant(OccupantKind kind, SeatState state) {
        return new Occupant("조용한 연필", new CharacterParts(0, 0, 0, 0), kind, state, START, 0, 1);
    }

    private static DurationRange range(Duration min, Duration max) {
        return new DurationRange(min, max);
    }
}
