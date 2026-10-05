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
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Sprint {

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

    private LocalDateTime closedAt;

    private Sprint(Long guestId, LocalDateTime now) {
        this.guestId = guestId;
        this.status = SprintStatus.OPEN;
        this.startedAt = now;
    }

    public static Sprint open(Long guestId, LocalDateTime now) {
        return new Sprint(guestId, now);
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
