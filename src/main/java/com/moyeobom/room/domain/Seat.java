package com.moyeobom.room.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import lombok.Getter;

/**
 * 자리 하나. 가상 메이트의 다음 상태 전이나 빈자리 채우기 예약을 함께 들고 있어 방이 정리될 때 취소한다.
 */
public class Seat {

    @Getter
    private final int seatNo;
    private Occupant occupant;
    private ScheduledFuture<?> scheduled;
    @Getter
    private Instant scheduledAt;

    public Seat(int seatNo) {
        this.seatNo = seatNo;
    }

    public Optional<Occupant> occupant() {
        return Optional.ofNullable(occupant);
    }

    public boolean isEmpty() {
        return occupant == null;
    }

    public void sit(Occupant occupant) {
        this.occupant = occupant;
    }

    public void leave() {
        this.occupant = null;
    }

    public void reserve(ScheduledFuture<?> scheduled, Instant at) {
        cancelReservation();
        this.scheduled = scheduled;
        this.scheduledAt = at;
    }

    public void cancelReservation() {
        if (scheduled != null) {
            scheduled.cancel(false);
        }
        this.scheduled = null;
        this.scheduledAt = null;
    }
}
