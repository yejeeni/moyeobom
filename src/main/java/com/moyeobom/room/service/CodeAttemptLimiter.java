package com.moyeobom.room.service;

import com.moyeobom.common.exception.BusinessException;
import com.moyeobom.common.exception.ErrorCode;
import com.moyeobom.common.ratelimit.SlidingWindowCounter;
import com.moyeobom.room.config.RoomProperties;
import java.time.Clock;
import java.util.function.Supplier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 입장 코드 대입 막기. 코드는 31자 × 6자리(약 8억 가지)라 한 곳에서 빠르게 대입하는 경우만 막으면 된다.
 * 요청한 곳(IP)마다 없는 코드를 넣은 시각을 window 동안 기억하고, maxFailures번을 넘으면 429로 거절한다.
 * 맞는 코드는 세지 않으므로 오타 몇 번으로 막히지 않는다.
 */
@Component
public class CodeAttemptLimiter {

    private final SlidingWindowCounter failures;

    public CodeAttemptLimiter(Clock clock, RoomProperties properties) {
        RoomProperties.CodeAttempts limits = properties.codeAttempts();
        this.failures = new SlidingWindowCounter(clock, limits.maxFailures(), limits.window());
    }

    /**
     * 막힌 곳이면 바로 거절하고, 아니면 action을 실행한다. action이 ROOM_NOT_FOUND로 끝나면 실패로 센다.
     */
    public <T> T attempt(String clientKey, Supplier<T> action) {
        if (failures.isFull(clientKey)) {
            throw new BusinessException(ErrorCode.TOO_MANY_CODE_ATTEMPTS);
        }
        try {
            return action.get();
        } catch (BusinessException e) {
            if (e.getErrorCode() == ErrorCode.ROOM_NOT_FOUND) {
                failures.record(clientKey);
            }
            throw e;
        }
    }

    @Scheduled(fixedDelayString = "${moyeobom.room.code-attempts.window}")
    void evictExpired() {
        failures.evictExpired();
    }
}
