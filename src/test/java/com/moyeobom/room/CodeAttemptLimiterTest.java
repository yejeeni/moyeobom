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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CodeAttemptLimiterTest {

    private static final String CLIENT = "203.0.113.7";

    MutableClock clock;
    CodeAttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-10T00:00:00Z"));
        RoomProperties properties = new RoomProperties(Duration.ofSeconds(60),
                new RoomProperties.CodeAttempts(3, Duration.ofMinutes(10)));
        limiter = new CodeAttemptLimiter(clock, properties);
    }

    @Test
    void 없는_코드를_정해진_횟수만큼_넣으면_맞는_코드도_잠시_막힌다() {
        fail(CLIENT);
        fail(CLIENT);
        fail(CLIENT);

        assertThatThrownBy(() -> limiter.attempt(CLIENT, () -> "입장"))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.TOO_MANY_CODE_ATTEMPTS);
    }

    @Test
    void 맞는_코드와_다른_오류는_세지_않는다() {
        for (int i = 0; i < 5; i++) {
            limiter.attempt(CLIENT, () -> "입장");
            assertThatThrownBy(() -> limiter.attempt(CLIENT, () -> {
                throw new BusinessException(ErrorCode.ROOM_FULL);
            })).isInstanceOf(BusinessException.class);
        }

        assertThat(limiter.attempt(CLIENT, () -> "입장")).isEqualTo("입장");
    }

    @Test
    void 기간이_지나면_오래된_실패부터_빠져_다시_시도할_수_있다() {
        fail(CLIENT);
        clock.advance(Duration.ofMinutes(5));
        fail(CLIENT);
        fail(CLIENT);

        // 첫 실패가 10분을 넘기면 실패가 2번으로 줄어 다시 들어갈 수 있다
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        assertThat(limiter.attempt(CLIENT, () -> "입장")).isEqualTo("입장");

        // 한 번 더 틀리면 다시 3번이 되어 막힌다
        fail(CLIENT);
        assertThatThrownBy(() -> limiter.attempt(CLIENT, () -> "입장"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void 요청한_곳마다_따로_센다() {
        fail(CLIENT);
        fail(CLIENT);
        fail(CLIENT);

        assertThat(limiter.attempt("198.51.100.2", () -> "입장")).isEqualTo("입장");
    }

    private void fail(String client) {
        assertThatThrownBy(() -> limiter.attempt(client, () -> {
            throw new BusinessException(ErrorCode.ROOM_NOT_FOUND);
        })).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(ErrorCode.ROOM_NOT_FOUND);
    }
}
