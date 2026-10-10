package com.moyeobom.room.service;

import com.moyeobom.common.exception.BusinessException;
import com.moyeobom.common.exception.ErrorCode;
import com.moyeobom.room.config.RoomProperties;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 입장 코드 대입 막기. 코드는 31자 × 6자리(약 8억 가지)라 한 곳에서 빠르게 대입하는 경우만 막으면 된다.
 * 요청한 곳(IP)마다 없는 코드를 넣은 시각을 window 동안 기억하고, maxFailures번을 넘으면 429로 거절한다.
 * 맞는 코드는 세지 않으므로 오타 몇 번으로 막히지 않는다. 서버 메모리에만 두며 재시작하면 초기화된다.
 */
@Component
public class CodeAttemptLimiter {

    private final Clock clock;
    private final RoomProperties.CodeAttempts limits;
    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();

    public CodeAttemptLimiter(Clock clock, RoomProperties properties) {
        this.clock = clock;
        this.limits = properties.codeAttempts();
    }

    /**
     * 막힌 곳이면 바로 거절하고, 아니면 action을 실행한다. action이 ROOM_NOT_FOUND로 끝나면 실패로 센다.
     */
    public <T> T attempt(String clientKey, Supplier<T> action) {
        if (isBlocked(clientKey)) {
            throw new BusinessException(ErrorCode.TOO_MANY_CODE_ATTEMPTS);
        }
        try {
            return action.get();
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.ROOM_NOT_FOUND) {
                recordFailure(clientKey);
            }
            throw e;
        }
    }

    boolean isBlocked(String clientKey) {
        Deque<Instant> recent = failures.computeIfPresent(clientKey, (key, times) -> prune(times));
        return recent != null && recent.size() >= limits.maxFailures();
    }

    private void recordFailure(String clientKey) {
        failures.compute(clientKey, (key, times) -> {
            Deque<Instant> recent = times == null ? new ArrayDeque<>() : prune(times);
            recent.addLast(clock.instant());
            return recent;
        });
    }

    /** window가 지난 실패를 버린다. 다 비면 null을 돌려줘 맵에서 항목을 지운다. */
    private Deque<Instant> prune(Deque<Instant> times) {
        Instant from = clock.instant().minus(limits.window());
        while (!times.isEmpty() && !times.peekFirst().isAfter(from)) {
            times.pollFirst();
        }
        return times.isEmpty() ? null : times;
    }

    /** 다시 오지 않는 곳의 기록이 쌓이지 않도록 주기적으로 정리한다. */
    @Scheduled(fixedDelayString = "${moyeobom.room.code-attempts.window}")
    void evictExpired() {
        failures.keySet().forEach(key -> failures.computeIfPresent(key, (k, times) -> prune(times)));
    }
}
