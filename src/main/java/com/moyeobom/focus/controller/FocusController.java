package com.moyeobom.focus.controller;

import com.moyeobom.common.auth.CurrentGuest;
import com.moyeobom.focus.dto.FocusDtos.FocusStopRequest;
import com.moyeobom.focus.dto.FocusDtos.MyStatusResponse;
import com.moyeobom.focus.service.FocusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "집중")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FocusController {

    private final FocusService focusService;

    @Operation(summary = "집중 시작", description = "다른 할 일의 세션이 진행 중이면 먼저 끝낸다. 완료한 할 일이면 409")
    @PostMapping("/tasks/{taskId}/focus")
    public MyStatusResponse start(@CurrentGuest Long guestId, @PathVariable Long taskId) {
        return focusService.start(guestId, taskId);
    }

    @Operation(summary = "중단 또는 휴식", description = "reason: STOPPED(대기 상태로), BREAK(휴식 상태로)")
    @PostMapping("/focus/stop")
    public MyStatusResponse stop(@CurrentGuest Long guestId, @Valid @RequestBody FocusStopRequest request) {
        return focusService.stop(guestId, request.reason());
    }

    @Operation(summary = "내 상태와 진행 중 세션", description = "집중 중이 아니면 session이 null")
    @GetMapping("/focus/current")
    public MyStatusResponse current(@CurrentGuest Long guestId) {
        return focusService.getStatus(guestId);
    }
}
