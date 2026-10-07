package com.moyeobom.room.service;

import com.moyeobom.room.domain.Room;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * roomId별 방, 코드별 방, 게스트별로 지금 있는 방. 서버가 재시작되면 모두 사라진다(코드도 무효가 된다).
 */
@Component
public class RoomRegistry {

    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final Map<String, String> roomIdByCode = new ConcurrentHashMap<>();
    private final Map<Long, String> roomIdByGuest = new ConcurrentHashMap<>();

    public Optional<Room> findByGuestId(Long guestId) {
        return Optional.ofNullable(roomIdByGuest.get(guestId)).map(rooms::get);
    }

    public Optional<Room> findById(String roomId) {
        return Optional.ofNullable(rooms.get(roomId));
    }

    public Optional<Room> findByCode(String code) {
        return Optional.ofNullable(roomIdByCode.get(code)).map(rooms::get);
    }

    public void register(Room room) {
        rooms.put(room.getRoomId(), room);
        roomIdByCode.put(room.getCode(), room.getRoomId());
    }

    public void remove(Room room) {
        rooms.remove(room.getRoomId(), room);
        roomIdByCode.remove(room.getCode(), room.getRoomId());
        room.members().forEach(guestId -> roomIdByGuest.remove(guestId, room.getRoomId()));
    }

    public void joined(Long guestId, Room room) {
        roomIdByGuest.put(guestId, room.getRoomId());
    }

    public void left(Long guestId, Room room) {
        roomIdByGuest.remove(guestId, room.getRoomId());
    }

    public boolean containsRoomId(String roomId) {
        return rooms.containsKey(roomId);
    }

    public boolean containsCode(String code) {
        return roomIdByCode.containsKey(code);
    }
}
