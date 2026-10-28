package com.moyeobom.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moyeobom.common.exception.BusinessException;
import com.moyeobom.common.exception.ErrorCode;
import com.moyeobom.room.config.RoomProperties;
import com.moyeobom.room.service.CodeAttemptLimiter;
import com.moyeobom.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 횟수 세기와 기간은 SlidingWindowCounterTest에서 확인하고, 여기서는 무엇을 실패로 세는지만 본다 */
class CodeAttemptLimiterTest {

    private static final String CLIENT = "203.0.113.7";

    @Test
    void 없는_코드만_실패로_세고_정해진_횟수를_넘으면_맞는_코드도_잠시_막힌다() {
        CodeAttemptLimiter limiter = new CodeAttemptLimiter(new MutableClock(Instant.parse("2026-10-10T00:00:00Z")),
                new RoomProperties(Duration.ofSeconds(60), 500, new RoomProperties.CodeAttempts(3, Duration.ofMinutes(10)), null));

        // 맞는 코드와 자리 다 참은 세지 않는다
        for (int i = 0; i < 5; i++) {
            limiter.attempt(CLIENT, () -> "입장");
            assertThatThrownBy(() -> limiter.attempt(CLIENT, () -> {
                throw new BusinessException(ErrorCode.ROOM_FULL);
            })).isInstanceOf(BusinessException.class);
        }
        assertThat(limiter.attempt(CLIENT, () -> "입장")).isEqualTo("입장");

        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> limiter.attempt(CLIENT, () -> {
                throw new BusinessException(ErrorCode.ROOM_NOT_FOUND);
            })).extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.ROOM_NOT_FOUND);
        }
        assertThatThrownBy(() -> limiter.attempt(CLIENT, () -> "입장"))
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_CODE_ATTEMPTS);
    }
}
