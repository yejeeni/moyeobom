package com.moyeobom.focus.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 할 일 하나에 대해 타이머를 시작해서 끝날 때까지의 기록. 시각은 모두 서버 시각이다.
 */
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FocusSession {

    /** 이보다 짧게 끝난 세션은 저장하지 않는다. */
    public static final Duration MIN_DURATION = Duration.ofSeconds(60);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long taskId;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    @Column(nullable = false)
    private LocalDateTime lastHeartbeatAt;

    private Integer durationSeconds;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private FocusEndReason endReason;

    private FocusSession(Long taskId, LocalDateTime now) {
        this.taskId = taskId;
        this.startedAt = now;
        this.lastHeartbeatAt = now;
    }

    public static FocusSession start(Long taskId, LocalDateTime now) {
        return new FocusSession(taskId, now);
    }

    /**
     * 세션을 끝낸다. 종료 시각이 시작보다 앞서면 시작 시각으로 맞춘다.
     */
    public void end(FocusEndReason reason, LocalDateTime endedAt) {
        if (!isRunning()) {
            throw new IllegalStateException("이미 끝난 세션입니다: " + id);
        }
        LocalDateTime end = endedAt.isBefore(startedAt) ? startedAt : endedAt;
        this.endedAt = end;
        this.endReason = reason;
        this.durationSeconds = Math.toIntExact(Duration.between(startedAt, end).toSeconds());
    }

    public boolean isRunning() {
        return endedAt == null;
    }

    /** 1분 미만이면 기록하지 않고 지운다. */
    public boolean isTooShort() {
        return durationSeconds != null && durationSeconds < MIN_DURATION.toSeconds();
    }

    public long elapsedSeconds(LocalDateTime now) {
        LocalDateTime end = isRunning() ? now : endedAt;
        return Math.max(0, Duration.between(startedAt, end).toSeconds());
    }
}
