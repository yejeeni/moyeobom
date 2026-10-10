package com.moyeobom.room.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param gracePeriod  WebSocket이 끊긴 뒤(또는 입장 후 연결 전) 방을 유지하는 시간
 * @param codeAttempts 없는 코드를 넣는 시도 제한. 코드를 하나씩 대입해 남의 방을 찾는 것을 막는다
 */
@ConfigurationProperties(prefix = "moyeobom.room")
public record RoomProperties(Duration gracePeriod, CodeAttempts codeAttempts) {

    /**
     * @param maxFailures window 안에 허용하는 실패 횟수. 넘으면 window가 지나 오래된 실패가 빠질 때까지 막는다
     * @param window      실패를 세는 기간
     */
    public record CodeAttempts(int maxFailures, Duration window) {
    }
}
