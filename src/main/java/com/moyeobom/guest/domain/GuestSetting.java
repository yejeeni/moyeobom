package com.moyeobom.guest.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuestSetting {

    public static final int DEFAULT_BREAK_ALERT_MINUTES = 50;

    @Id
    private Long guestId;

    @Column(nullable = false)
    private boolean breakAlertEnabled;

    @Column(nullable = false)
    private int breakAlertMinutes;

    private GuestSetting(Long guestId) {
        this.guestId = guestId;
        this.breakAlertEnabled = false;
        this.breakAlertMinutes = DEFAULT_BREAK_ALERT_MINUTES;
    }

    public static GuestSetting defaultOf(Long guestId) {
        return new GuestSetting(guestId);
    }

    public void change(Boolean breakAlertEnabled, Integer breakAlertMinutes) {
        if (breakAlertEnabled != null) {
            this.breakAlertEnabled = breakAlertEnabled;
        }
        if (breakAlertMinutes != null) {
            this.breakAlertMinutes = breakAlertMinutes;
        }
    }
}
