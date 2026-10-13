package com.moyeobom.guest.service;

import com.moyeobom.common.exception.BusinessException;
import com.moyeobom.common.exception.ErrorCode;
import com.moyeobom.common.ratelimit.SlidingWindowCounter;
import com.moyeobom.guest.config.GuestProperties;
import java.time.Clock;
import java.util.function.Supplier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 게스트 발급은 인증 없이 부를 수 있어, 요청한 곳(IP)마다 window 동안 maxIssues번까지만 받는다.
 * 게스트마다 열람실을 하나씩 만들 수 있으므로 서버 메모리 보호도 겸한다.
 */
@Component
public class GuestIssueLimiter {

    private final SlidingWindowCounter issues;

    public GuestIssueLimiter(Clock clock, GuestProperties properties) {
        GuestProperties.IssueLimit limit = properties.issueLimit();
        this.issues = new SlidingWindowCounter(clock, limit.maxIssues(), limit.window());
    }

    public <T> T attempt(String clientKey, Supplier<T> issue) {
        if (issues.isFull(clientKey)) {
            throw new BusinessException(ErrorCode.TOO_MANY_GUESTS);
        }
        T issued = issue.get();
        issues.record(clientKey);
        return issued;
    }

    @Scheduled(fixedDelayString = "${moyeobom.guest.issue-limit.window}")
    void evictExpired() {
        issues.evictExpired();
    }
}
