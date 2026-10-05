package com.moyeobom.room.dto;

import com.moyeobom.room.domain.CharacterParts;
import com.moyeobom.room.domain.Occupant;
import com.moyeobom.room.domain.SeatState;
import java.time.Instant;
import java.util.List;

public final class RoomDtos {

    private RoomDtos() {
    }

    public record RoomEnterResponse(String roomId, int seatNo, String nickname) {
    }

    /**
     * 모든 WebSocket 이벤트를 담는 봉투. 실제 유저와 가상 메이트의 이벤트 형식이 같다.
     */
    public record RoomEvent(RoomEventType type, String roomId, Integer seatNo, Instant at, Object payload) {
    }

    public enum RoomEventType {
        ROOM_SNAPSHOT,
        SEAT_JOINED,
        SEAT_LEFT,
        STATE_CHANGED,
        COUNTS_CHANGED,
        BREAK_ALERT,
        /** 서버 재시작 등으로 방이 없을 때. 클라이언트는 POST /rooms/enter를 다시 부른다. */
        ROOM_NOT_FOUND
    }

    public record OccupantView(String nickname, CharacterParts character, SeatState state, Instant since,
                               int completedCount, int remainingCount) {

        public static OccupantView of(Occupant occupant) {
            return new OccupantView(occupant.getNickname(), occupant.getCharacter(), occupant.getState(),
                    occupant.getSince(), occupant.getCompletedCount(), occupant.getRemainingCount());
        }
    }

    /** 빈자리는 occupant가 null이다. */
    public record SeatView(int seatNo, OccupantView occupant) {
    }

    public record SnapshotPayload(Instant serverTime, int mySeatNo, List<SeatView> seats) {
    }

    public record StatePayload(SeatState state, Instant since) {
    }

    public record CountsPayload(int completedCount, int remainingCount) {
    }

    public record NoticePayload(String message) {
    }
}
