package com.moyeobom.room.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import lombok.Getter;

/**
 * 자리 하나. 실제 사람 자리와 가상 메이트 자리로 나뉜다.
 * 가상 자리는 메이트의 다음 상태 전이나 빈자리 채우기 예약을 함께 들고 있어 방이 정리될 때 취소한다.
 */
public class Seat {

    @Getter
    private final int seatNo;
    @Getter
    private final SeatType type;
    private Occupant occupant;
    private ScheduledFuture<?> scheduled;
    @Getter
    private Instant scheduledAt;

    public Seat(int seatNo, SeatType type) {
        this.seatNo = seatNo;
        this.type = type;
    }

    public Optional<Occupant> occupant() {
        return Optional.ofNullable(occupant);
    }

    public boolean isEmpty() {
        return occupant == null;
    }

    public boolean isReal() {
        return type == SeatType.REAL;
    }

    /** 비어 있는 실제 사람 자리. 클라이언트에 '초대 대기'로 보인다. */
    public boolean isWaiting() {
        return isReal() && isEmpty();
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
