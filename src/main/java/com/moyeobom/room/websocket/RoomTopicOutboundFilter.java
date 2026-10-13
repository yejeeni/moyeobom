package com.moyeobom.room.websocket;

import com.moyeobom.room.service.RoomMessenger;
import com.moyeobom.room.service.RoomRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

/**
 * 방 알림은 지금 그 방에 있는 사람에게만 보낸다.
 * 방을 나가도 서버는 구독을 끊지 않으므로, 클라이언트가 구독을 남겨 두면 나간 방의 알림을 계속 받을 수 있다.
 * 브로커가 세션마다 메시지를 내보낼 때 그 세션의 게스트가 방에 없으면 버린다.
 */
@Component
@RequiredArgsConstructor
public class RoomTopicOutboundFilter implements ChannelInterceptor {

    private final ConnectionRegistry connectionRegistry;
    private final RoomRegistry roomRegistry;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        MessageHeaders headers = message.getHeaders();
        if (SimpMessageHeaderAccessor.getMessageType(headers) != SimpMessageType.MESSAGE) {
            return message;
        }
        String destination = SimpMessageHeaderAccessor.getDestination(headers);
        if (destination == null || !destination.startsWith(RoomMessenger.ROOM_TOPIC_PREFIX)) {
            return message;
        }
        String roomId = destination.substring(RoomMessenger.ROOM_TOPIC_PREFIX.length());
        String sessionId = SimpMessageHeaderAccessor.getSessionId(headers);
        boolean member = sessionId != null && connectionRegistry.guestOf(sessionId)
                .flatMap(guestId -> roomRegistry.findById(roomId).map(room -> room.hasMember(guestId)))
                .orElse(false);
        return member ? message : null;
    }
}
