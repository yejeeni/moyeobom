package com.moyeobom.sprint.dto;

import com.moyeobom.sprint.domain.Sprint;
import com.moyeobom.task.domain.Task;
import com.moyeobom.task.dto.TaskDtos.CarriedTaskRequest;
import com.moyeobom.task.dto.TaskDtos.TaskCreateRequest;
import com.moyeobom.task.dto.TaskDtos.TaskResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class SprintDtos {

    private SprintDtos() {
    }

    /**
     * 새 할 일과 이월 할 일을 합쳐 1개 이상이어야 한다.
     * plannedMinutes는 오늘 공부할 시간(타임박스, 30~720분). 보내지 않으면 타임박스 없이 시작한다.
     */
    public record SprintStartRequest(
            @Size(max = Task.MAX_PER_SPRINT) List<@Valid TaskCreateRequest> tasks,
            @Size(max = Task.MAX_PER_SPRINT) List<@Valid CarriedTaskRequest> carriedTasks,
            @Min(Sprint.PLANNED_MINUTES_MIN) @Max(Sprint.PLANNED_MINUTES_MAX) Integer plannedMinutes
    ) {

        public SprintStartRequest {
            tasks = tasks == null ? List.of() : tasks;
            carriedTasks = carriedTasks == null ? List.of() : carriedTasks;
        }
    }

    /** 열린 스프린트가 없으면 sprint가 null이다. */
    public record CurrentSprintResponse(SprintView sprint) {
    }

    /** plannedMinutes와 endsAt은 타임박스가 없으면 null이다. */
    public record SprintView(Long sprintId, Instant startedAt, Integer plannedMinutes, Instant endsAt,
                             List<TaskResponse> tasks) {
    }

    /** 타임박스 바꾸기(연장 등). 시작 시각부터 센 전체 시간이다 */
    public record SprintPlanRequest(
            @NotNull @Min(Sprint.PLANNED_MINUTES_MIN) @Max(Sprint.PLANNED_MINUTES_MAX) Integer plannedMinutes
    ) {
    }

    public record CarryoverResponse(List<CarryoverTask> tasks) {
    }

    /**
     * 계획 화면에 미리 채우는 이월 할 일. 예상 시간은 남은 작업 기준으로 다시 받는다.
     */
    public record CarryoverTask(Long taskId, String title, Integer previousEstimatedMinutes,
                                long cumulativeSeconds) {
    }
}
