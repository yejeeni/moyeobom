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
    void 처음_입장하면_메이트_4에서_8명이_이미_공부하던_중이고_나머지_자리는_나중에_채워지며_인원은_방마다_다르다() {
        List<Occupant> mates = room.virtualSeats().stream().flatMap(seat -> seat.occupant().stream()).toList();

        assertThat(mates).hasSizeBetween(4, 8).allMatch(Occupant::isVirtual);
        // 아직 대기 중인 나까지 포함해 앉은 사람의 과반이 집중 중이다
        assertThat(room.focusingCount()).isGreaterThanOrEqualTo(MateFactory.majorityOf(room.occupiedCount()));
        assertThat(mates).allSatisfy(mate -> {
            assertThat(mate.getSince()).isBeforeOrEqualTo(START);
            assertThat(mate.getCompletedCount()).isBetween(0, 3);
            assertThat(mate.getRemainingCount()).isBetween(1, 4);
        });
        for (Seat seat : room.virtualSeats()) {
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

        // 처음 인원은 방마다 다르다(나 포함 5~9명)
        Set<Long> counts = new HashSet<>();
        for (long seed = 0; seed < 30; seed++) {
            mateScheduler = newMateScheduler(seed);
            counts.add(createRoom().occupiedCount());
        }
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
        Seat empty = sparse.virtualSeats().stream().filter(Seat::isEmpty).findFirst().orElseThrow();
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
            for (Seat seat : room.virtualSeats()) {
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
            List<Seat> occupiedBefore = room.virtualSeats().stream().filter(seat -> !seat.isEmpty()).toList();
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
        Room full = Room.solo("r-full", Room.MAX_SEAT_COUNT, 1);
        full.seat(1).sit(occupant(OccupantKind.REAL, SeatState.FOCUS));
        List<Seat> mates = full.virtualSeats();
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
        Room small = Room.solo("r-small", Room.MAX_SEAT_COUNT, 1);
        small.seat(1).sit(occupant(OccupantKind.REAL, SeatState.IDLE));
        List<Seat> mates = small.virtualSeats();
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
    void 집중_계속과_자리_유지를_모두_켜면_하루_동안_메이트가_쉬거나_자리를_뜨지_않고_빈자리는_채워진다() {
        Room focused = roomWith(true, true);

        assertThat(mates(focused)).allMatch(Occupant::isFocusing);
        Instant end = START.plus(Duration.ofHours(24));
        while (scheduler.runNext(end)) {
            assertThat(mates(focused)).allMatch(Occupant::isFocusing);
            for (Seat seat : focused.virtualSeats()) {
                assertScheduledWithinRange(seat);
            }
        }

        assertThat(focused.virtualSeats()).noneMatch(Seat::isEmpty);
        assertThat(events).doesNotContain(RoomEventType.SEAT_LEFT).contains(RoomEventType.SEAT_JOINED);
    }

    @Test
    void 집중_계속만_켜면_쉬지_않지만_집중이_끝날_때_가끔_자리를_뜨고_새_메이트가_채운다() {
        Room focused = roomWith(true, false);

        Instant end = START.plus(Duration.ofHours(24));
        while (scheduler.runNext(end)) {
            assertThat(mates(focused)).allMatch(Occupant::isFocusing);
        }

        assertThat(events).contains(RoomEventType.SEAT_LEFT, RoomEventType.SEAT_JOINED);
    }

    @Test
    void 자리_유지만_켜면_쉬기는_하지만_자리를_뜨지_않는다() {
        Room staying = roomWith(false, true);

        Instant end = START.plus(Duration.ofHours(24));
        boolean rested = false;
        while (scheduler.runNext(end)) {
            rested |= mates(staying).stream().anyMatch(mate -> mate.getState() == SeatState.BREAK);
            // 쉬는 메이트가 있어도 과반 집중 규칙은 그대로다
            assertThat(staying.focusingCount()).isGreaterThanOrEqualTo(MateFactory.majorityOf(staying.occupiedCount()));
        }

        assertThat(rested).isTrue();
        assertThat(events).doesNotContain(RoomEventType.SEAT_LEFT);
    }

    @Test
    void 집중_계속을_켜면_쉬던_메이트가_바로_집중으로_돌아가고_모두_끄면_다시_쉬고_자리를_뜬다() {
        room.close();
        Room mixed = Room.solo("r-mixed", 3, 1);
        mixed.seat(1).sit(occupant(OccupantKind.REAL, SeatState.FOCUS));
        Seat resting = mixed.seat(2);
        mateScheduler.seat(mixed, resting,
                new PlacedMate(occupant(OccupantKind.VIRTUAL, SeatState.BREAK), START.plus(Duration.ofMinutes(10))));
        mateScheduler.seat(mixed, mixed.seat(3),
                new PlacedMate(occupant(OccupantKind.VIRTUAL, SeatState.FOCUS), START.plus(Duration.ofMinutes(30))));
        clock.set(START.plus(Duration.ofMinutes(1)));

        mateScheduler.changeMateSettings(mixed, true, true);

        Occupant mate = resting.occupant().orElseThrow();
        assertThat(mixed.isMatesAlwaysFocus()).isTrue();
        assertThat(mixed.isMatesStay()).isTrue();
        assertThat(mate.getState()).isEqualTo(SeatState.FOCUS);
        assertThat(mate.getSince()).isEqualTo(clock.instant());
        assertThat(events).containsExactly(RoomEventType.STATE_CHANGED);
        // 쉬는 동안 잡혀 있던 예약(10분 뒤 집중)은 집중 시간 예약으로 바뀐다
        assertThat(Duration.between(clock.instant(), resting.getScheduledAt()))
                .isBetween(Duration.ofMinutes(25), Duration.ofMinutes(90));

        mateScheduler.changeMateSettings(mixed, false, false);
        Instant end = START.plus(Duration.ofHours(24));
        boolean rested = false;
        while (scheduler.runNext(end)) {
            rested |= mates(mixed).stream().anyMatch(occupant -> occupant.getState() == SeatState.BREAK);
        }
        assertThat(rested).isTrue();
        assertThat(events).contains(RoomEventType.SEAT_LEFT);
    }

    @Test
    void 정리된_방의_예약은_아무것도_하지_않는다() {
        room.close();

        assertThat(scheduler.pendingCount()).isZero();
        assertThat(scheduler.runNext(START.plus(Duration.ofHours(24)))).isFalse();
        assertThat(events).isEmpty();
    }

    private Room createRoom() {
        Room created = Room.solo("r-test", Room.MAX_SEAT_COUNT, 5);
        created.seat(5).sit(occupant(OccupantKind.REAL, SeatState.IDLE));
        mateScheduler.populate(created, START);
        return created;
    }

    /** 메이트 설정을 정해 만든 9자리 방. 기본으로 만든 방의 예약이 같은 스케줄러에서 함께 돌지 않게 그 방은 닫는다 */
    private Room roomWith(boolean alwaysFocus, boolean stay) {
        room.close();
        Room created = Room.solo("r-settings", Room.MAX_SEAT_COUNT, 5);
        created.seat(5).sit(occupant(OccupantKind.REAL, SeatState.IDLE));
        mateScheduler.changeMateSettings(created, alwaysFocus, stay);
        mateScheduler.populate(created, START);
        return created;
    }

    private static List<Occupant> mates(Room target) {
        return target.virtualSeats().stream().flatMap(seat -> seat.occupant().stream()).toList();
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
