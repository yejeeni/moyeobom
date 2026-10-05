package com.moyeobom.mate.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "moyeobom.mate")
public record MateProperties(
        DurationRange focusDuration,
        DurationRange breakDuration,
        double completeProbability,
        double leaveProbability,
        DurationRange refillDelay,
        int majorityFocusCount,
        DurationRange focusExtension,
        Initial initial
) {

    public record DurationRange(Duration min, Duration max) {

        public DurationRange {
            if (min.compareTo(max) > 0) {
                throw new IllegalArgumentException("min은 max보다 클 수 없습니다: " + min + " > " + max);
            }
        }
    }

    /**
     * 방을 처음 만들 때 "이미 공부하던 중"인 메이트의 초기 상태.
     */
    public record Initial(
            double focusRatio,
            int completedCountMax,
            int remainingCountMin,
            int remainingCountMax
    ) {
    }
}
