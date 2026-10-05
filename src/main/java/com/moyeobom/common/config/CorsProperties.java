package com.moyeobom.common.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * REST API와 WebSocket을 열어 줄 프론트엔드 주소.
 */
@ConfigurationProperties(prefix = "moyeobom.cors")
public record CorsProperties(List<String> allowedOrigins) {

    public CorsProperties {
        allowedOrigins = allowedOrigins == null ? List.of() : List.copyOf(allowedOrigins);
    }
}
