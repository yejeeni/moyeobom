package com.moyeobom.guest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moyeobom.common.exception.BusinessException;
import com.moyeobom.common.exception.ErrorCode;
import com.moyeobom.guest.config.GuestProperties;
import com.moyeobom.guest.service.GuestIssueLimiter;
import com.moyeobom.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class GuestIssueLimiterTest {

    @Test
    void 같은_곳에서_정해진_횟수를_넘게_받으면_잠시_막히고_기간이_지나면_풀린다() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-10T00:00:00Z"));
        GuestIssueLimiter limiter = new GuestIssueLimiter(clock,
                new GuestProperties(new GuestProperties.IssueLimit(2, Duration.ofMinutes(10))));

        limiter.attempt("203.0.113.7", () -> "게스트1");
        limiter.attempt("203.0.113.7", () -> "게스트2");

        assertThatThrownBy(() -> limiter.attempt("203.0.113.7", () -> "게스트3"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_GUESTS);
        assertThat(limiter.attempt("198.51.100.2", () -> "다른 곳")).isEqualTo("다른 곳");

        clock.advance(Duration.ofMinutes(10).plusSeconds(1));
        assertThat(limiter.attempt("203.0.113.7", () -> "게스트3")).isEqualTo("게스트3");
    }
}
