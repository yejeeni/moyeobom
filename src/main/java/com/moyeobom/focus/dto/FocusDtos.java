package com.moyeobom.focus.dto;

import com.moyeobom.room.domain.SeatState;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;

public final class FocusDtos {

    private FocusDtos() {
    }

    public enum StopReason {
        STOPPED,
        BREAK
    }

    public record FocusStopRequest(@NotNull StopReason reason) {
    }

    /**
     * 내 상태. 집중 중이면 session이 있고, 경과 시간은 서버 시각 기준이다.
     */
    public record MyStatusResponse(SeatState state, Instant since, Instant serverTime, SessionView session) {
    }

    public record SessionView(Long sessionId, Long taskId, Instant startedAt, long elapsedSeconds) {
    }
}
