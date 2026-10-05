package com.moyeobom.mate.service;

import com.moyeobom.mate.config.MateProperties;
import com.moyeobom.mate.config.MateProperties.DurationRange;
import com.moyeobom.room.domain.CharacterParts;
import com.moyeobom.room.domain.Nicknames;
import com.moyeobom.room.domain.Occupant;
import com.moyeobom.room.domain.OccupantKind;
import com.moyeobom.room.domain.SeatState;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.random.RandomGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 가상 메이트를 만들고 지속 시간을 무작위로 정한다. 실제 유저와 같은 규칙으로 닉네임과 캐릭터를 받는다.
 */
@Component
@RequiredArgsConstructor
public class MateFactory {

    private static final Duration MIN_REMAINING = Duration.ofMinutes(1);

    private final MateProperties properties;
    private final RandomGenerator random;

    /**
     * 방을 처음 만들 때 "이미 공부하던 중"인 메이트들. 경과 시간이 제각각 보이도록 since를 앞당긴다.
     */
    public List<PlacedMate> createInitial(int count, Instant now) {
        int focusCount = Math.min(count, Math.max(properties.majorityFocusCount(),
                (int) Math.round(count * properties.initial().focusRatio())));
        List<SeatState> states = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            states.add(i < focusCount ? SeatState.FOCUS : SeatState.BREAK);
        }
        Collections.shuffle(states, random);

        return states.stream().map(state -> placeAlreadyStudying(state, now)).toList();
    }

    /** 빈자리에 새로 들어오는 메이트. 집중 상태로 시작한다. */
    public PlacedMate createNewcomer(Instant now) {
        MateProperties.Initial initial = properties.initial();
        Occupant occupant = newOccupant(SeatState.FOCUS, now, 0,
                between(initial.remainingCountMin(), initial.remainingCountMax()));
        return new PlacedMate(occupant, now.plus(focusDuration()));
    }

    public Duration focusDuration() {
        return pick(properties.focusDuration());
    }

    public Duration breakDuration() {
        return pick(properties.breakDuration());
    }

    public Duration focusExtension() {
        return pick(properties.focusExtension());
    }

    public Duration refillDelay() {
        return pick(properties.refillDelay());
    }

    public boolean rollComplete() {
        return random.nextDouble() < properties.completeProbability();
    }

    public boolean rollLeave() {
        return random.nextDouble() < properties.leaveProbability();
    }

    /** 남은 일을 다 끝낸 메이트가 새로 적는 할 일 수 */
    public int newRemainingCount() {
        MateProperties.Initial initial = properties.initial();
        return between(initial.remainingCountMin(), initial.remainingCountMax());
    }

    public int majorityFocusCount() {
        return properties.majorityFocusCount();
    }

    private PlacedMate placeAlreadyStudying(SeatState state, Instant now) {
        Duration total = state == SeatState.FOCUS ? focusDuration() : breakDuration();
        long maxElapsed = Math.max(0, total.minus(MIN_REMAINING).toSeconds());
        Duration elapsed = Duration.ofSeconds(random.nextLong(maxElapsed + 1));
        Instant since = now.minus(elapsed);

        MateProperties.Initial initial = properties.initial();
        Occupant occupant = newOccupant(state, since, between(0, initial.completedCountMax()),
                between(initial.remainingCountMin(), initial.remainingCountMax()));
        return new PlacedMate(occupant, since.plus(total));
    }

    private Occupant newOccupant(SeatState state, Instant since, int completed, int remaining) {
        return new Occupant(Nicknames.random(random), CharacterParts.random(random), OccupantKind.VIRTUAL,
                state, since, completed, remaining);
    }

    private Duration pick(DurationRange range) {
        long min = range.min().toSeconds();
        long max = range.max().toSeconds();
        return Duration.ofSeconds(min + random.nextLong(max - min + 1));
    }

    private int between(int min, int max) {
        return min + random.nextInt(max - min + 1);
    }

    /**
     * @param nextTransitionAt 다음 상태 전이 시각
     */
    public record PlacedMate(Occupant occupant, Instant nextTransitionAt) {
    }
}
