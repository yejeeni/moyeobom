package com.moyeobom.room.websocket;

import com.moyeobom.common.auth.GuestAuthInterceptor;
import com.moyeobom.guest.service.GuestService;
import com.moyeobom.room.service.RoomMessenger;
import com.moyeobom.room.service.RoomRegistry;
import java.security.Principal;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.simp.user.UserDestinationMessageHandler;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * 클라이언트에서 들어오는 STOMP 프레임을 검사한다.
 * <ul>
 *   <li>CONNECT: X-Guest-Id 헤더로 게스트를 확인한다. 모르는 게스트면 연결을 거부한다.</li>
 *   <li>SUBSCRIBE: 다른 사람의 열람실 토픽은 막는다.</li>
 *   <li>SEND: WebSocket은 서버 → 클라이언트 단방향이라 막는다. 동작은 REST API로 보낸다.</li>
 *   <li>모든 프레임(연결 확인 신호 포함): 마지막 신호 시각을 기록한다.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class StompGuestInterceptor implements ExecutorChannelInterceptor {

    static final String USER_SNAPSHOT_DESTINATION = "/user" + RoomMessenger.SNAPSHOT_QUEUE;

    private final GuestService guestService;
    private final RoomRegistry roomRegistry;
    private final ConnectionRegistry connectionRegistry;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }
        if (accessor.getSessionId() != null) {
            connectionRegistry.touch(accessor.getSessionId(), clock.instant());
        }
        StompCommand command = accessor.getCommand();
        if (command == StompCommand.CONNECT) {
            String publicId = accessor.getFirstNativeHeader(GuestAuthInterceptor.GUEST_HEADER);
            Long guestId = guestService.findIdByPublicId(publicId)
                    .orElseThrow(() -> new MessageDeliveryException("GUEST_NOT_FOUND"));
            accessor.setUser(new GuestPrincipal(guestId));
        } else if (command == StompCommand.SUBSCRIBE) {
            checkSubscription(guestIdOf(accessor), accessor.getDestination());
        } else if (command == StompCommand.SEND) {
            throw new MessageDeliveryException("SEND_NOT_ALLOWED");
        }
        return message;
    }

    /**
     * 스냅샷 큐 구독이 브로커에 등록된 뒤에 스냅샷을 보내야 유실되지 않는다.
     */
    @Override
    public void afterMessageHandled(Message<?> message, MessageChannel channel, MessageHandler handler,
                                    Exception ex) {
        if (ex != null || !(handler instanceof UserDestinationMessageHandler)) {
            return;
        }
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor != null && accessor.getCommand() == StompCommand.SUBSCRIBE
                && USER_SNAPSHOT_DESTINATION.equals(accessor.getDestination())) {
            eventPublisher.publishEvent(new SnapshotRequestedEvent(guestIdOf(accessor)));
        }
    }

    private void checkSubscription(Long guestId, String destination) {
        if (destination == null) {
            throw new MessageDeliveryException("DESTINATION_REQUIRED");
        }
        if (destination.startsWith(RoomMessenger.ROOM_TOPIC_PREFIX)) {
            String roomId = destination.substring(RoomMessenger.ROOM_TOPIC_PREFIX.length());
            boolean othersRoom = roomRegistry.findById(roomId)
                    .map(room -> !room.hasMember(guestId))
                    .orElse(false);
            if (othersRoom) {
                throw new MessageDeliveryException("ROOM_FORBIDDEN");
            }
            return;
        }
        if (!destination.startsWith("/user/queue/")) {
            throw new MessageDeliveryException("DESTINATION_FORBIDDEN");
        }
    }

    private static Long guestIdOf(StompHeaderAccessor accessor) {
        Principal user = accessor.getUser();
        if (user instanceof GuestPrincipal principal) {
            return principal.guestId();
        }
        throw new MessageDeliveryException("GUEST_NOT_FOUND");
    }

    public record SnapshotRequestedEvent(Long guestId) {
    }
}
