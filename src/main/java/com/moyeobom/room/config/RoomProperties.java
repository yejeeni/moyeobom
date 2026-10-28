package com.moyeobom.room.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param gracePeriod  WebSocket이 끊긴 뒤(또는 입장 후 연결 전) 방을 유지하는 시간
 * @param maxRooms     서버 한 대에 동시에 둘 수 있는 열람실 수. 방마다 메모리와 가상 메이트 예약 작업을 쓴다
 * @param codeAttempts 없는 코드를 넣는 시도 제한. 코드를 하나씩 대입해 남의 방을 찾는 것을 막는다
 * @param restoreWindow 서버가 켜진 뒤 사라진 방을 같은 코드로 복구할 수 있는 시간. 없으면 10분
 */
@ConfigurationProperties(prefix = "moyeobom.room")
public record RoomProperties(Duration gracePeriod, int maxRooms, CodeAttempts codeAttempts, Duration restoreWindow) {

    public RoomProperties {
        restoreWindow = restoreWindow == null ? Duration.ofMinutes(10) : restoreWindow;
    }

    /**
     * @param maxFailures window 안에 허용하는 실패 횟수. 넘으면 window가 지나 오래된 실패가 빠질 때까지 막는다
     * @param window      실패를 세는 기간
     */
    public record CodeAttempts(int maxFailures, Duration window) {
    }
}
