package com.moyeobom.room.service;

import com.moyeobom.room.domain.Room;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * roomId별 방과 게스트별 roomId. 서버가 재시작되면 모두 사라진다.
 */
@Component
public class RoomRegistry {

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<Long, String> roomIdByGuest = new ConcurrentHashMap<>();

    public Optional<Room> findByGuestId(Long guestId) {
        return Optional.ofNullable(roomIdByGuest.get(guestId)).map(rooms::get);
    }

    public Optional<Room> findById(String roomId) {
        return Optional.ofNullable(rooms.get(roomId));
    }

    public void register(Room room) {
        rooms.put(room.getRoomId(), room);
        roomIdByGuest.put(room.getOwnerGuestId(), room.getRoomId());
    }

    public void remove(Room room) {
        rooms.remove(room.getRoomId(), room);
        roomIdByGuest.remove(room.getOwnerGuestId(), room.getRoomId());
    }

    public boolean containsRoomId(String roomId) {
        return rooms.containsKey(roomId);
    }
}
