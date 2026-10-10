package com.moyeobom.guest.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param issueLimit 같은 곳(IP)에서 게스트를 새로 받는 횟수 제한. 스크립트로 게스트를 대량으로 만드는 것을 막는다
 */
@ConfigurationProperties(prefix = "moyeobom.guest")
public record GuestProperties(IssueLimit issueLimit) {

    /**
     * @param maxIssues window 안에 허용하는 발급 횟수. 학교·회사처럼 IP를 나눠 쓰는 곳을 생각해 넉넉히 둔다
     * @param window    발급을 세는 기간
     */
    public record IssueLimit(int maxIssues, Duration window) {
    }
}
