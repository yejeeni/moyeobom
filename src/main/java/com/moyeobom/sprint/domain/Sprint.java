package com.moyeobom.sprint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * open_guest_id 생성 컬럼은 DB가 관리하므로 매핑하지 않는다.
 * plannedMinutes는 타임박스(계획한 공부 시간)다. 시간이 지나도 스프린트를 자동으로 닫지 않고, 마무리를 권하기만 한다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sprint {

    public static final int PLANNED_MINUTES_MIN = 30;
    public static final int PLANNED_MINUTES_MAX = 720;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long guestId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SprintStatus status;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    /** 계획한 공부 시간(분). 타임박스 없이 시작한 스프린트는 null */
    private Integer plannedMinutes;

    private LocalDateTime closedAt;

    private Sprint(Long guestId, LocalDateTime now, Integer plannedMinutes) {
        this.guestId = guestId;
        this.status = SprintStatus.OPEN;
        this.startedAt = now;
        this.plannedMinutes = plannedMinutes;
    }

    public static Sprint open(Long guestId, LocalDateTime now) {
        return new Sprint(guestId, now, null);
    }

    public static Sprint open(Long guestId, LocalDateTime now, Integer plannedMinutes) {
        return new Sprint(guestId, now, plannedMinutes);
    }

    /** 타임박스를 늘리거나 줄인다. "30분 더" 같은 연장에 쓴다 */
    public void changePlannedMinutes(int minutes) {
        if (!isOpen()) {
            throw new IllegalStateException("닫힌 스프린트의 계획 시간은 바꿀 수 없습니다: " + id);
        }
        this.plannedMinutes = minutes;
    }

    /** 타임박스가 끝나는 시각. 타임박스가 없으면 null */
    public LocalDateTime endsAt() {
        return plannedMinutes == null ? null : startedAt.plusMinutes(plannedMinutes);
    }

    public void close(LocalDateTime now) {
        if (status == SprintStatus.CLOSED) {
            throw new IllegalStateException("이미 닫힌 스프린트입니다: " + id);
        }
        this.status = SprintStatus.CLOSED;
        this.closedAt = now;
    }

    public boolean isOpen() {
        return status == SprintStatus.OPEN;
    }
}
