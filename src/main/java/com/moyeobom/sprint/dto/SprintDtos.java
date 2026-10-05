package com.moyeobom.sprint.dto;

import com.moyeobom.task.dto.TaskDtos.CarriedTaskRequest;
import com.moyeobom.task.dto.TaskDtos.TaskCreateRequest;
import com.moyeobom.task.dto.TaskDtos.TaskResponse;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;

public final class SprintDtos {

    private SprintDtos() {
    }

    /**
     * 새 할 일과 이월 할 일을 합쳐 1개 이상이어야 한다.
     */
    public record SprintStartRequest(
            List<@Valid TaskCreateRequest> tasks,
            List<@Valid CarriedTaskRequest> carriedTasks
    ) {

        public SprintStartRequest {
            tasks = tasks == null ? List.of() : tasks;
            carriedTasks = carriedTasks == null ? List.of() : carriedTasks;
        }
    }

    /** 열린 스프린트가 없으면 sprint가 null이다. */
    public record CurrentSprintResponse(SprintView sprint) {
    }

    public record SprintView(Long sprintId, Instant startedAt, List<TaskResponse> tasks) {
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
