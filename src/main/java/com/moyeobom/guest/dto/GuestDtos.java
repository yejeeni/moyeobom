package com.moyeobom.guest.dto;

import jakarta.validation.constraints.Positive;
import java.time.Instant;

public final class GuestDtos {

    private GuestDtos() {
    }

    public record GuestCreateResponse(String guestId) {
    }

    public record GuestMeResponse(String guestId, Instant createdAt, boolean hasOpenSprint) {
    }

    public record SettingResponse(boolean breakAlertEnabled, int breakAlertMinutes) {
    }

    public record SettingUpdateRequest(Boolean breakAlertEnabled, @Positive Integer breakAlertMinutes) {
    }
}
