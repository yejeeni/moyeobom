package com.moyeobom.guest;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moyeobom.common.exception.BusinessException;
import com.moyeobom.common.exception.ErrorCode;
import com.moyeobom.guest.config.GuestProperties;
import com.moyeobom.guest.service.GuestIssueLimiter;
import com.moyeobom.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

/** 횟수 세기와 기간은 SlidingWindowCounterTest에서 확인하고, 여기서는 발급을 막는지만 본다 */
class GuestIssueLimiterTest {

    @Test
    void 같은_곳에서_정해진_횟수를_넘게_받으면_429로_막힌다() {
        GuestIssueLimiter limiter = new GuestIssueLimiter(new MutableClock(Instant.parse("2026-10-10T00:00:00Z")),
                new GuestProperties(new GuestProperties.IssueLimit(2, Duration.ofMinutes(10))));

        limiter.attempt("203.0.113.7", () -> "게스트1");
        limiter.attempt("203.0.113.7", () -> "게스트2");

        assertThatThrownBy(() -> limiter.attempt("203.0.113.7", () -> "게스트3"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_GUESTS);
    }
}
