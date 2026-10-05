package com.moyeobom.task.dto;

import com.moyeobom.common.time.Times;
import com.moyeobom.task.domain.Task;
import com.moyeobom.task.domain.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public final class TaskDtos {

    private TaskDtos() {
    }

    public record TaskCreateRequest(
            @NotBlank @Size(max = Task.TITLE_MAX_LENGTH) String title,
            @Positive Integer estimatedMinutes
    ) {
    }

    public record CarriedTaskRequest(
            @NotNull Long fromTaskId,
            @Positive Integer estimatedMinutes
    ) {
    }

    /**
     * 보낸 항목만 바꾼다.
     */
    public record TaskUpdateRequest(
            @Size(max = Task.TITLE_MAX_LENGTH) String title,
            @Positive Integer estimatedMinutes,
            Integer sortOrder
    ) {
    }

    public record TaskResponse(
            Long taskId,
            String title,
            Integer estimatedMinutes,
            TaskStatus status,
            int sortOrder,
            Long carriedFromTaskId,
            Instant completedAt,
            long actualSeconds,
            long cumulativeSeconds,
            boolean hasRecords
    ) {

        public static TaskResponse of(Task task, TaskTimes times) {
            return new TaskResponse(task.getId(), task.getTitle(), task.getEstimatedMinutes(), task.getStatus(),
                    task.getSortOrder(), task.getCarriedFromTaskId(), Times.toInstant(task.getCompletedAt()),
                    times.actualSeconds(), times.cumulativeSeconds(), times.sessionCount() > 0);
        }
    }

    public record TaskCompleteResponse(TaskResponse task, String refreshMessage) {
    }

    /**
     * 할 일 하나의 집중 시간 집계. 끝난 세션만 시간에 더한다.
     */
    public record TaskTimes(long actualSeconds, long cumulativeSeconds, long sessionCount) {

        public static final TaskTimes ZERO = new TaskTimes(0, 0, 0);
    }
}
