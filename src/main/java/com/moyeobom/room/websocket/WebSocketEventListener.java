package com.moyeobom.room.websocket;

import com.moyeobom.focus.service.FocusService;
import com.moyeobom.room.service.RoomService;
import com.moyeobom.room.websocket.StompGuestInterceptor.SnapshotRequestedEvent;
import java.security.Principal;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/**
 * 연결, 끊김, 스냅샷 요청을 열람실과 집중 세션에 전달한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final ConnectionRegistry connectionRegistry;
    private final RoomService roomService;
    private final FocusService focusService;
    private final Clock clock;

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        Principal user = event.getUser();
        String sessionId = (String) event.getMessage().getHeaders().get("simpSessionId");
        if (user instanceof GuestPrincipal principal && sessionId != null) {
            connectionRegistry.connected(sessionId, principal.guestId(), clock.instant());
            roomService.onConnected(principal.guestId());
        }
    }

    /**
     * 게스트의 마지막 연결이 끊기면 진행 중인 세션을 마지막 신호 시각으로 닫고, 열람실 자리는 잠시 맡아 둔다.
     */
    @EventListener
    public void onDisconnected(SessionDisconnectEvent event) {
        connectionRegistry.disconnected(event.getSessionId()).ifPresent(disconnection -> {
            log.debug("연결 끊김 guestId={}, lastSignalAt={}", disconnection.guestId(), disconnection.lastSignalAt());
            try {
                focusService.endForDisconnect(disconnection.guestId(), disconnection.lastSignalAt());
            } finally {
                roomService.onDisconnected(disconnection.guestId());
            }
        });
    }

    @EventListener
    public void onSnapshotRequested(SnapshotRequestedEvent event) {
        roomService.sendSnapshot(event.guestId());
    }

    /**
     * 연결된 게스트의 진행 중 세션에 1분마다 확인 신호 시각을 남긴다. 서버가 죽으면 이 시각으로 세션을 닫는다.
     */
    @Scheduled(fixedDelayString = "${moyeobom.focus.heartbeat-interval:PT1M}",
            initialDelayString = "${moyeobom.focus.heartbeat-interval:PT1M}")
    public void recordHeartbeats() {
        focusService.touchHeartbeat(connectionRegistry.connectedGuestIds());
    }
}
