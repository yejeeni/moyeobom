package com.moyeobom.common.config;

import com.moyeobom.room.websocket.StompGuestInterceptor;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * 서버가 열람실 변화를 알려주는 단방향 STOMP 통로.
 * 연결 확인 신호는 양방향 10초이며, 심플 브로커는 신호 간격의 3배(30초) 동안 아무것도 오지 않으면 연결을 끊는다.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final long HEARTBEAT_MILLIS = 10_000;

    private final StompGuestInterceptor stompGuestInterceptor;
    private final TaskScheduler taskScheduler;
    private final CorsProperties corsProperties;

    public WebSocketConfig(StompGuestInterceptor stompGuestInterceptor,
                           @Qualifier(SchedulingConfig.TASK_SCHEDULER) TaskScheduler taskScheduler,
                           CorsProperties corsProperties) {
        this.stompGuestInterceptor = stompGuestInterceptor;
        this.taskScheduler = taskScheduler;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOrigins(corsProperties.allowedOrigins().toArray(String[]::new));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(new long[]{HEARTBEAT_MILLIS, HEARTBEAT_MILLIS})
                .setTaskScheduler(taskScheduler);
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompGuestInterceptor);
    }
}
