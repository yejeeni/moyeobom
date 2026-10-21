package com.moyeobom.common.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import com.moyeobom.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class SlidingWindowCounterTest {

    private static final String CLIENT = "203.0.113.7";

    MutableClock clock = new MutableClock(Instant.parse("2026-10-10T00:00:00Z"));
    SlidingWindowCounter counter = new SlidingWindowCounter(clock, 3, Duration.ofMinutes(10));

    @Test
    void 기간_안에_정해진_횟수가_쌓이면_가득_차고_오래된_기록부터_빠진다() {
        counter.record(CLIENT);
        clock.advance(Duration.ofMinutes(5));
        counter.record(CLIENT);
        assertThat(counter.isFull(CLIENT)).isFalse();

        counter.record(CLIENT);
        assertThat(counter.isFull(CLIENT)).isTrue();

        // 첫 기록이 10분을 넘기면 두 번만 남는다
        clock.advance(Duration.ofMinutes(5).plusSeconds(1));
        assertThat(counter.isFull(CLIENT)).isFalse();
    }

    @Test
    void 요청한_곳마다_따로_센다() {
        counter.record(CLIENT);
        counter.record(CLIENT);
        counter.record(CLIENT);

        assertThat(counter.isFull(CLIENT)).isTrue();
        assertThat(counter.isFull("198.51.100.2")).isFalse();
    }
}
