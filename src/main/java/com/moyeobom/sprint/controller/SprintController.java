package com.moyeobom.sprint.controller;

import com.moyeobom.common.auth.CurrentGuest;
import com.moyeobom.sprint.dto.ReviewDtos.ReviewResponse;
import com.moyeobom.sprint.dto.ReviewDtos.SprintCloseRequest;
import com.moyeobom.sprint.dto.ReviewDtos.SprintCloseResponse;
import com.moyeobom.sprint.dto.SprintDtos.CarryoverResponse;
import com.moyeobom.sprint.dto.SprintDtos.CurrentSprintResponse;
import com.moyeobom.sprint.dto.SprintDtos.SprintStartRequest;
import com.moyeobom.sprint.service.ReviewService;
import com.moyeobom.sprint.service.SprintService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "스프린트")
@RestController
@RequestMapping("/api/v1/sprints")
@RequiredArgsConstructor
public class SprintController {

    private final SprintService sprintService;
    private final ReviewService reviewService;

    @Operation(summary = "열린 스프린트와 할 일 목록", description = "열린 스프린트가 없으면 sprint가 null")
    @GetMapping("/current")
    public CurrentSprintResponse current(@CurrentGuest Long guestId) {
        return sprintService.getCurrent(guestId);
    }

    @Operation(summary = "이월할 할 일", description = "마지막으로 닫힌 스프린트에서 이월하기로 한 할 일")
    @GetMapping("/carryover")
    public CarryoverResponse carryover(@CurrentGuest Long guestId) {
        return sprintService.getCarryover(guestId);
    }

    @Operation(summary = "스프린트 시작", description = "새 할 일과 이월 할 일을 합쳐 1개 이상 필요")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CurrentSprintResponse start(@CurrentGuest Long guestId, @Valid @RequestBody SprintStartRequest request) {
        return sprintService.start(guestId, request);
    }

    @Operation(summary = "회고 조회", description = """
            할 일별 예상과 실제 비교, 요약. 끝난 세션만 집계하므로 오늘 마무리를 누르면
            진행 중인 집중을 먼저 POST /focus/stop(STOPPED)으로 끝낸 뒤 조회한다.
            """)
    @GetMapping("/current/review")
    public ReviewResponse review(@CurrentGuest Long guestId) {
        return reviewService.getReview(guestId);
    }

    @Operation(summary = "오늘 마무리 확정", description = """
            미완료 할 일마다 CARRY(이월) 또는 DROP(닫기)를 고른다. 보내지 않은 할 일은 이월한다.
            진행 중인 세션은 SPRINT_CLOSED로 닫히고 열람실에서 퇴장한다.
            """)
    @PostMapping("/current/close")
    public SprintCloseResponse close(@CurrentGuest Long guestId, @Valid @RequestBody SprintCloseRequest request) {
        return reviewService.close(guestId, request);
    }
}
