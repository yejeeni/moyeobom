package com.moyeobom.task.controller;

import com.moyeobom.common.auth.CurrentGuest;
import com.moyeobom.task.dto.TaskDtos.TaskCompleteResponse;
import com.moyeobom.task.dto.TaskDtos.TaskCreateRequest;
import com.moyeobom.task.dto.TaskDtos.TaskResponse;
import com.moyeobom.task.dto.TaskDtos.TaskUpdateRequest;
import com.moyeobom.task.service.TaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "할 일")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @Operation(summary = "할 일 추가", description = "열린 스프린트에 추가한다")
    @PostMapping("/sprints/current/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskResponse add(@CurrentGuest Long guestId, @Valid @RequestBody TaskCreateRequest request) {
        return taskService.add(guestId, request);
    }

    @Operation(summary = "제목, 예상 시간, 순서 변경", description = "보낸 항목만 바꾼다")
    @PatchMapping("/tasks/{taskId}")
    public TaskResponse update(@CurrentGuest Long guestId, @PathVariable Long taskId,
                               @Valid @RequestBody TaskUpdateRequest request) {
        return taskService.update(guestId, taskId, request);
    }

    @Operation(summary = "기록 없는 할 일 삭제", description = "집중 기록이 있으면 409 TASK_HAS_RECORDS")
    @DeleteMapping("/tasks/{taskId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@CurrentGuest Long guestId, @PathVariable Long taskId) {
        taskService.delete(guestId, taskId);
    }

    @Operation(summary = "완료 처리", description = "진행 중인 집중 세션이 이 할 일이면 함께 끝낸다. 리프레시 문구를 돌려준다")
    @PostMapping("/tasks/{taskId}/complete")
    public TaskCompleteResponse complete(@CurrentGuest Long guestId, @PathVariable Long taskId) {
        return taskService.complete(guestId, taskId);
    }
}
