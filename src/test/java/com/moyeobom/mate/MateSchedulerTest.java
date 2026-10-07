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
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MateSchedulerTest {

    static final Instant START = Instant.parse("2026-10-05T09:00:00Z");
    static final MateProperties PROPERTIES = new MateProperties(
            range(Duration.ofMinutes(25), Duration.ofMinutes(90)),
            range(Duration.ofMinutes(5), Duration.ofMinutes(15)),
            0.4, 0.2,
            range(Duration.ofSeconds(30), Duration.ofMinutes(2)),
            range(Duration.ofMinutes(5), Duration.ofMinutes(10)),
            new MateProperties.Initial(4, 8, range(Duration.ofMinutes(1), Duration.ofMinutes(15)), 0.8, 3, 1, 4));

    MutableClock clock;
    FakeTaskScheduler scheduler;
    RoomMessenger messenger;
    MateScheduler mateScheduler;
    List<RoomEventType> events;
    Room room;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        scheduler = new FakeTaskScheduler(clock);
        events = new ArrayList<>();
        messenger = mock(RoomMessenger.class);
        doAnswer(invocation -> events.add(invocation.getArgument(1)))
                .when(messenger).toRoom(any(), any(), any(), any());
        mateScheduler = newMateScheduler(42);
        room = createRoom();
    }

    @Test
    void 처음_입장하면_메이트_4에서_8명이_이미_공부하던_중이고_나머지_자리는_나중에_채워진다() {
        List<Occupant> mates = room.mateSeats().stream().flatMap(seat -> seat.occupant().stream()).toList();

        assertThat(mates).hasSizeBetween(4, 8).allMatch(Occupant::isVirtual);
        // 아직 대기 중인 나까지 포함해 앉은 사람의 과반이 집중 중이다
        assertThat(room.focusingCount()).isGreaterThanOrEqualTo(MateFactory.majorityOf(room.occupiedCount()));
        assertThat(mates).allSatisfy(mate -> {
            assertThat(mate.getSince()).isBeforeOrEqualTo(START);
            assertThat(mate.getCompletedCount()).isBetween(0, 3);
            assertThat(mate.getRemainingCount()).isBetween(1, 4);
        });
        for (Seat seat : room.mateSeats()) {
            assertThat(seat.getScheduledAt()).isAfter(START);
            if (seat.isEmpty()) {
                assertThat(Duration.between(START, seat.getScheduledAt()))
                        .isBetween(Duration.ofMinutes(1), Duration.ofMinutes(15));
                continue;
            }
            Occupant mate = seat.occupant().orElseThrow();
            Duration total = Duration.between(mate.getSince(), seat.getScheduledAt());
            if (mate.isFocusing()) {
                assertThat(total).isBetween(Duration.ofMinutes(25), Duration.ofMinutes(90));
            } else {
                assertThat(total).isBetween(Duration.ofMinutes(5), Duration.ofMinutes(15));
            }
        }
    }

    @Test
    void 방마다_처음_인원이_다르다() {
        Set<Long> counts = new HashSet<>();
        for (long seed = 0; seed < 30; seed++) {
            mateScheduler = newMateScheduler(seed);
            counts.add(createRoom().occupiedCount());
        }

        // 나 포함 5~9명
        assertThat(counts).hasSizeGreaterThan(2).allMatch(count -> count >= 5 && count <= 9);
    }

    @Test
    void 처음에_빈_자리는_시간이_지나면_새_메이트가_집중_상태로_들어온다() {
        mateScheduler = newMateScheduler(1);
        Room sparse = createRoom();
        for (long seed = 2; sparse.occupiedCount() == Room.MAX_SEAT_COUNT; seed++) {
            mateScheduler = newMateScheduler(seed);
            sparse = createRoom();
        }
        Seat empty = sparse.mateSeats().stream().filter(Seat::isEmpty).findFirst().orElseThrow();
        Instant arrivalAt = empty.getScheduledAt();

        while (empty.isEmpty() && scheduler.runNext(START.plus(Duration.ofMinutes(15)))) {
            // 그 자리가 채워질 때까지 돌린다
        }

        Occupant newcomer = empty.occupant().orElseThrow();
        assertThat(newcomer.getState()).isEqualTo(SeatState.FOCUS);
        assertThat(newcomer.getSince()).isEqualTo(arrivalAt);
        assertThat(events).contains(RoomEventType.SEAT_JOINED);
    }

    @Test
    void 하루_동안_돌려도_항상_과반이_집중_중이고_지속_시간은_범위_안이다() {
        Instant end = START.plus(Duration.ofHours(24));
        int transitions = 0;
        while (scheduler.runNext(end)) {
            transitions++;
            assertThat(room.focusingCount()).isGreaterThanOrEqualTo(MateFactory.majorityOf(room.occupiedCount()));
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
        while (leftSeat == null) {
            List<Seat> occupiedBefore = room.mateSeats().stream().filter(seat -> !seat.isEmpty()).toList();
            if (!scheduler.runNext(end)) {
                break;
            }
            leftSeat = occupiedBefore.stream().filter(Seat::isEmpty).findFirst().orElse(null);
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
    void 쉬면_집중_인원이_과반보다_적어질_때는_휴식_대신_집중을_연장한다() {
        // 9명 중 나와 메이트 4명만 집중 중이고 나머지는 휴식 중인 방
        Room full = new Room("r-full", 2L, 1);
        full.mySeat().sit(occupant(OccupantKind.REAL, SeatState.FOCUS));
        List<Seat> mates = full.mateSeats();
        for (int i = 0; i < mates.size(); i++) {
            SeatState state = i < 4 ? SeatState.FOCUS : SeatState.BREAK;
            Instant next = state == SeatState.FOCUS ? START.plus(Duration.ofMinutes(1)) : START.plus(Duration.ofHours(10));
            mateScheduler.seat(full, mates.get(i), new PlacedMate(occupant(OccupantKind.VIRTUAL, state), next));
        }

        scheduler.runNext(START.plus(Duration.ofMinutes(1)));

        // 첫 메이트가 쉬면 9명 중 4명만 집중하게 되므로 연장된다
        assertThat(full.focusingCount()).isEqualTo(5);
        Seat extended = mates.getFirst();
        assertThat(extended.occupant().orElseThrow().getState()).isEqualTo(SeatState.FOCUS);
        assertThat(Duration.between(START.plus(Duration.ofMinutes(1)), extended.getScheduledAt()))
                .isBetween(Duration.ofMinutes(5), Duration.ofMinutes(10));
    }

    @Test
    void 사람이_적은_방은_과반_기준도_낮아진다() {
        // 나(대기)와 집중 중인 메이트 4명, 5명짜리 방. 과반은 3명이다.
        // 첫 메이트는 쉬어도 3명이 집중하므로 쉴 수 있다
        Room small = new Room("r-small", 3L, 1);
        small.mySeat().sit(occupant(OccupantKind.REAL, SeatState.IDLE));
        List<Seat> mates = small.mateSeats();
        for (int i = 0; i < 4; i++) {
            mateScheduler.seat(small, mates.get(i),
                    new PlacedMate(occupant(OccupantKind.VIRTUAL, SeatState.FOCUS), START.plus(Duration.ofMinutes(1 + i))));
        }

        scheduler.runNext(START.plus(Duration.ofMinutes(1)));

        assertThat(mates.getFirst().occupant().orElseThrow().getState()).isEqualTo(SeatState.BREAK);
        assertThat(small.focusingCount()).isEqualTo(3);

        scheduler.runNext(START.plus(Duration.ofMinutes(2)));

        // 두 번째 메이트까지 쉬면 5명 중 2명만 집중하므로 연장된다
        assertThat(mates.get(1).occupant().orElseThrow().getState()).isEqualTo(SeatState.FOCUS);
        assertThat(small.focusingCount()).isEqualTo(3);
    }

    @Test
    void 정리된_방의_예약은_아무것도_하지_않는다() {
        room.close();

        assertThat(scheduler.pendingCount()).isZero();
        assertThat(scheduler.runNext(START.plus(Duration.ofHours(24)))).isFalse();
        assertThat(events).isEmpty();
    }

    private Room createRoom() {
        Room created = new Room("r-test", 1L, 5);
        created.mySeat().sit(occupant(OccupantKind.REAL, SeatState.IDLE));
        mateScheduler.populate(created, START);
        return created;
    }

    private MateScheduler newMateScheduler(long seed) {
        return new MateScheduler(new MateFactory(PROPERTIES, new Random(seed)), messenger, scheduler, clock);
    }

    private void assertScheduledWithinRange(Seat seat) {
        Instant now = clock.instant();
        if (seat.getScheduledAt() == null || !seat.getScheduledAt().isAfter(now)) {
            return;
        }
        Duration until = Duration.between(now, seat.getScheduledAt());
        if (seat.isEmpty()) {
            // 처음 빈 자리는 최대 15분, 퇴장한 자리는 최대 2분 뒤에 채워진다
            assertThat(until).isLessThanOrEqualTo(Duration.ofMinutes(15));
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
