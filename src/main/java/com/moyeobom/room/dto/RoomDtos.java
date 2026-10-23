package com.moyeobom.room.dto;

import com.moyeobom.room.domain.CharacterParts;
import com.moyeobom.room.domain.Occupant;
import com.moyeobom.room.domain.Room;
import com.moyeobom.room.domain.SeatState;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public final class RoomDtos {

    private RoomDtos() {
    }

    /**
     * 새 열람실 구성. 합계는 1~9명이다.
     *
     * @param virtualSeats 가상 메이트 자리 수(0~8)
     * @param realSeats    실제 사람 자리 수(나 포함 1~9)
     * @param matesAlwaysFocus 가상 메이트가 쉬지 않고 계속 집중한다. 없으면 false
     * @param matesStay        가상 메이트가 자리를 뜨지 않는다. 없으면 false
     */
    public record RoomCreateRequest(
            @NotNull @Min(0) @Max(Room.MAX_SEAT_COUNT - 1) Integer virtualSeats,
            @NotNull @Min(1) @Max(Room.MAX_SEAT_COUNT) Integer realSeats,
            Boolean matesAlwaysFocus,
            Boolean matesStay
    ) {
    }

    /** 방 설정 바꾸기. 방 주인만 바꿀 수 있고, 보낸 항목만 바꾼다 */
    public record RoomSettingsRequest(Boolean matesAlwaysFocus, Boolean matesStay) {
    }

    /**
     * 방 설정.
     *
     * @param ownerSeatNo 설정을 바꿀 수 있는 사람(방 주인)의 자리 번호
     */
    public record RoomSettingsView(boolean matesAlwaysFocus, boolean matesStay, int ownerSeatNo) {
    }

    public record RoomJoinRequest(@NotBlank String code) {
    }

    /**
     * 내가 있는 열람실.
     *
     * @param code          입장 코드. 실제 자리가 2개 이상이면 다른 사람에게 나눠 준다
     * @param realSeatCount 실제 사람 자리 수(나 포함)
     * @param owner         내가 방 주인(설정을 바꿀 수 있는 사람)인지
     */
    public record RoomEnterResponse(String roomId, String code, int seatCount, int realSeatCount, int seatNo,
                                    String nickname, boolean matesAlwaysFocus, boolean matesStay, boolean owner) {
    }

    /** 코드로 들어가기 전에 확인하는 열람실 정보 */
    public record RoomLookupResponse(String code, int seatCount, int realSeatCount, int waitingSeatCount) {
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
        /** 방 설정이나 방 주인이 바뀌었다. 본문은 RoomSettingsView */
        ROOM_SETTINGS_CHANGED,
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

    /**
     * 빈자리는 occupant가 null이다.
     *
     * @param waiting 비어 있는 실제 사람 자리(코드로 누군가 들어오기를 기다리는 자리)
     */
    public record SeatView(int seatNo, OccupantView occupant, boolean waiting) {
    }

    public record SnapshotPayload(Instant serverTime, String code, int seatCount, int mySeatNo, List<SeatView> seats,
                                  boolean matesAlwaysFocus, boolean matesStay, int ownerSeatNo) {
    }

    /** 실제 사람이 나가 자리가 다시 초대 대기가 되면 waiting이 true다. 가상 메이트가 나가면 본문이 없다 */
    public record SeatLeftPayload(boolean waiting) {
    }

    public record StatePayload(SeatState state, Instant since) {
    }

    public record CountsPayload(int completedCount, int remainingCount) {
    }

    public record NoticePayload(String message) {
    }
}
