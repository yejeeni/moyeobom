package com.moyeobom.guest.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Guest {

    /** 마지막 접속 시각은 이 간격보다 오래됐을 때만 갱신해 쓰기를 줄인다. */
    private static final Duration LAST_SEEN_UPDATE_INTERVAL = Duration.ofMinutes(1);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 36, columnDefinition = "char(36)")
    private String publicId;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime lastSeenAt;

    private Guest(String publicId, LocalDateTime now) {
        this.publicId = publicId;
        this.createdAt = now;
        this.lastSeenAt = now;
    }

    public static Guest issue(LocalDateTime now) {
        return new Guest(UUID.randomUUID().toString(), now);
    }

    public void touch(LocalDateTime now) {
        if (Duration.between(lastSeenAt, now).compareTo(LAST_SEEN_UPDATE_INTERVAL) >= 0) {
            this.lastSeenAt = now;
        }
    }
}
