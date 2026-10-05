package com.moyeobom.guest.controller;

import com.moyeobom.common.auth.CurrentGuest;
import com.moyeobom.common.time.Times;
import com.moyeobom.guest.domain.Guest;
import com.moyeobom.guest.dto.GuestDtos.GuestCreateResponse;
import com.moyeobom.guest.dto.GuestDtos.GuestMeResponse;
import com.moyeobom.guest.dto.GuestDtos.SettingResponse;
import com.moyeobom.guest.dto.GuestDtos.SettingUpdateRequest;
import com.moyeobom.guest.service.GuestService;
import com.moyeobom.sprint.service.SprintQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "게스트")
@RestController
@RequestMapping("/api/v1/guests")
@RequiredArgsConstructor
public class GuestController {

    private final GuestService guestService;
    private final SprintQueryService sprintQueryService;

    @Operation(summary = "게스트 발급", description = "받은 guestId를 브라우저에 저장하고 이후 요청의 X-Guest-Id 헤더로 보낸다")
    @SecurityRequirements
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GuestCreateResponse issue() {
        return new GuestCreateResponse(guestService.issue().getPublicId());
    }

    @Operation(summary = "내 게스트 정보와 열린 스프린트 여부")
    @GetMapping("/me")
    public GuestMeResponse me(@CurrentGuest Long guestId) {
        Guest guest = guestService.getGuest(guestId);
        return new GuestMeResponse(guest.getPublicId(), Times.toInstant(guest.getCreatedAt()),
                sprintQueryService.hasOpenSprint(guestId));
    }

    @Operation(summary = "휴식 알림 설정 조회")
    @GetMapping("/me/setting")
    public SettingResponse getSetting(@CurrentGuest Long guestId) {
        return guestService.getSetting(guestId);
    }

    @Operation(summary = "휴식 알림 설정 변경", description = "보낸 항목만 바꾼다")
    @PatchMapping("/me/setting")
    public SettingResponse changeSetting(@CurrentGuest Long guestId,
                                         @Valid @RequestBody SettingUpdateRequest request) {
        return guestService.changeSetting(guestId, request);
    }
}
