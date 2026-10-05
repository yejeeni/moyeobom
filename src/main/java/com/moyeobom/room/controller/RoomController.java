package com.moyeobom.room.controller;

import com.moyeobom.common.auth.CurrentGuest;
import com.moyeobom.room.dto.RoomDtos.RoomEnterResponse;
import com.moyeobom.room.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "열람실")
@RestController
@RequestMapping("/api/v1/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @Operation(summary = "열람실 입장", description = """
            자리와 닉네임을 배정한다. 이미 방이 있으면 그 방을 돌려준다. 열린 스프린트에 할 일이 없으면 409.
            이후 /ws에 STOMP로 연결해 /topic/rooms/{roomId}, /user/queue/room-snapshot, /user/queue/notices를 구독한다.
            """)
    @PostMapping("/enter")
    public RoomEnterResponse enter(@CurrentGuest Long guestId) {
        return roomService.enter(guestId);
    }
}
