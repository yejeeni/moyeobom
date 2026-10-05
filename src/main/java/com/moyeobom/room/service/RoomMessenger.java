package com.moyeobom.room.service;

import com.moyeobom.room.domain.Room;
import com.moyeobom.room.dto.RoomDtos.RoomEvent;
import com.moyeobom.room.dto.RoomDtos.RoomEventType;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * 열람실 이벤트를 STOMP 구독 주소로 보낸다.
 */
@Component
@RequiredArgsConstructor
public class RoomMessenger {

    public static final String ROOM_TOPIC_PREFIX = "/topic/rooms/";
    public static final String SNAPSHOT_QUEUE = "/queue/room-snapshot";
    public static final String NOTICE_QUEUE = "/queue/notices";

    private final SimpMessagingTemplate messagingTemplate;
    private final Clock clock;

    public void toRoom(Room room, RoomEventType type, Integer seatNo, Object payload) {
        messagingTemplate.convertAndSend(ROOM_TOPIC_PREFIX + room.getRoomId(),
                new RoomEvent(type, room.getRoomId(), seatNo, clock.instant(), payload));
    }

    public void toUser(Long guestId, String queue, RoomEvent event) {
        messagingTemplate.convertAndSendToUser(String.valueOf(guestId), queue, event);
    }

    public RoomEvent event(RoomEventType type, String roomId, Integer seatNo, Object payload) {
        return new RoomEvent(type, roomId, seatNo, clock.instant(), payload);
    }
}
