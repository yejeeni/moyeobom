package com.moyeobom.common.ratelimit;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 키(요청한 IP 등)마다 최근 window 동안 일어난 일을 세는 카운터. 서버 메모리에만 두며 재시작하면 초기화된다.
 * 다시 오지 않는 키의 기록이 쌓이지 않도록 쓰는 쪽에서 evictExpired()를 주기적으로 부른다.
 */
public class SlidingWindowCounter {

    private final Clock clock;
    private final int maxEvents;
    private final Duration window;
    private final Map<String, Deque<Instant>> events = new ConcurrentHashMap<>();

    public SlidingWindowCounter(Clock clock, int maxEvents, Duration window) {
        this.clock = clock;
        this.maxEvents = maxEvents;
        this.window = window;
    }

    /** window 안에 이미 maxEvents번이 쌓였으면 true */
    public boolean isFull(String key) {
        Deque<Instant> recent = events.computeIfPresent(key, (k, times) -> prune(times));
        return recent != null && recent.size() >= maxEvents;
    }

    public void record(String key) {
        events.compute(key, (k, times) -> {
            Deque<Instant> recent = times == null ? new ArrayDeque<>() : prune(times);
            if (recent == null) {
                recent = new ArrayDeque<>();
            }
            recent.addLast(clock.instant());
            return recent;
        });
    }

    public void evictExpired() {
        events.keySet().forEach(key -> events.computeIfPresent(key, (k, times) -> prune(times)));
    }

    /** window가 지난 기록을 버린다. 다 비면 null을 돌려줘 맵에서 항목을 지운다. */
    private Deque<Instant> prune(Deque<Instant> times) {
        Instant from = clock.instant().minus(window);
        while (!times.isEmpty() && !times.peekFirst().isAfter(from)) {
            times.pollFirst();
        }
        return times.isEmpty() ? null : times;
    }
}
