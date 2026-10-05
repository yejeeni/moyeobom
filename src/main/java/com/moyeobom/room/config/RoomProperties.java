package com.moyeobom.room.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param gracePeriod WebSocket이 끊긴 뒤(또는 입장 후 연결 전) 방을 유지하는 시간
 */
@ConfigurationProperties(prefix = "moyeobom.room")
public record RoomProperties(Duration gracePeriod) {
}
