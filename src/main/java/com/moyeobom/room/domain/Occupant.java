package com.moyeobom.room.domain;

import java.time.Instant;
import lombok.Getter;

/**
 * 자리에 앉은 사람. 방 락을 잡은 스레드만 바꾼다.
 */
@Getter
public class Occupant {

    private final String nickname;
    private final CharacterParts character;
    private final OccupantKind kind;
    private SeatState state;
    private Instant since;
    private int completedCount;
    private int remainingCount;

    public Occupant(String nickname, CharacterParts character, OccupantKind kind, SeatState state, Instant since,
                    int completedCount, int remainingCount) {
        this.nickname = nickname;
        this.character = character;
        this.kind = kind;
        this.state = state;
        this.since = since;
        this.completedCount = completedCount;
        this.remainingCount = remainingCount;
    }

    public void changeState(SeatState state, Instant since) {
        if (kind == OccupantKind.VIRTUAL && state == SeatState.IDLE) {
            throw new IllegalArgumentException("가상 메이트는 대기 상태가 되지 않습니다.");
        }
        this.state = state;
        this.since = since;
    }

    public void changeCounts(int completedCount, int remainingCount) {
        this.completedCount = completedCount;
        this.remainingCount = remainingCount;
    }

    public boolean isFocusing() {
        return state == SeatState.FOCUS;
    }

    public boolean isVirtual() {
        return kind == OccupantKind.VIRTUAL;
    }
}
