package com.moyeobom.room.controller;

import com.moyeobom.common.auth.CurrentGuest;
import com.moyeobom.room.dto.RoomDtos.RoomCreateRequest;
import com.moyeobom.room.dto.RoomDtos.RoomEnterResponse;
import com.moyeobom.room.dto.RoomDtos.RoomJoinRequest;
import com.moyeobom.room.dto.RoomDtos.RoomLookupResponse;
import com.moyeobom.room.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "열람실")
@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @Operation(summary = "열람실 만들기", description = """
            가상 메이트 자리(0~8)와 실제 사람 자리(나 포함 1~9)를 정해 새 열람실을 만들고 들어간다. 합계는 9명까지.
            입장 코드를 돌려주며, 실제 자리가 남아 있는 동안 코드를 아는 사람은 누구나 들어올 수 있다.
            이미 다른 방에 있으면 그 방에서 나온다. 열린 스프린트에 할 일이 없으면 409.
            이후 /ws에 STOMP로 연결해 /topic/rooms/{roomId}, /user/queue/room-snapshot, /user/queue/notices를 구독한다.
            """)
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoomEnterResponse create(@CurrentGuest Long guestId, @Valid @RequestBody RoomCreateRequest request) {
        return roomService.create(guestId, request.virtualSeats(), request.realSeats());
    }

    @Operation(summary = "코드로 들어가기", description = "코드가 없으면 404 ROOM_NOT_FOUND, 실제 자리가 다 찼으면 409 ROOM_FULL")
    @PostMapping("/join")
    public RoomEnterResponse join(@CurrentGuest Long guestId, @Valid @RequestBody RoomJoinRequest request) {
        return roomService.join(guestId, request.code());
    }

    @Operation(summary = "코드 확인", description = "들어가기 전에 방이 있는지, 남은 실제 자리가 몇 개인지 본다")
    @GetMapping("/lookup")
    public RoomLookupResponse lookup(@RequestParam String code) {
        return roomService.lookup(code);
    }

    @Operation(summary = "내가 있는 열람실", description = "없으면 404 ROOM_NOT_FOUND(서버 재시작 등). 그때는 새로 만들거나 코드로 들어간다")
    @GetMapping("/current")
    public RoomEnterResponse current(@CurrentGuest Long guestId) {
        return roomService.current(guestId);
    }

    @Operation(summary = "열람실에서 나오기", description = "실제 참여자가 모두 나가면 방이 정리되고 코드도 무효가 된다")
    @PostMapping("/leave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void leave(@CurrentGuest Long guestId) {
        roomService.leave(guestId);
    }
}
