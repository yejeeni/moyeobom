package com.moyeobom.mate.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 가상 메이트 행동 규칙. 집중 인원은 항상 자리에 앉은 사람의 과반을 유지한다.
 */
@ConfigurationProperties(prefix = "moyeobom.mate")
public record MateProperties(
        DurationRange focusDuration,
        DurationRange breakDuration,
        double completeProbability,
        double leaveProbability,
        DurationRange refillDelay,
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
     * 방을 처음 만들 때의 상태.
     *
     * @param mateCountMin  처음 앉아 있는 메이트 수의 최솟값
     * @param mateCountMax  처음 앉아 있는 메이트 수의 최댓값
     * @param arrivalDelay  처음에 빈 자리에 새 메이트가 들어오기까지 걸리는 시간
     * @param focusRatio    "이미 공부하던 중"인 메이트 중 집중 상태의 비율
     */
    public record Initial(
            int mateCountMin,
            int mateCountMax,
            DurationRange arrivalDelay,
            double focusRatio,
            int completedCountMax,
            int remainingCountMin,
            int remainingCountMax
    ) {

        public Initial {
            if (mateCountMin < 0 || mateCountMin > mateCountMax) {
                throw new IllegalArgumentException("메이트 수 범위가 올바르지 않습니다: " + mateCountMin + "~" + mateCountMax);
            }
        }
    }
}
