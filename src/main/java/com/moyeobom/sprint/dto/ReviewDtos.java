package com.moyeobom.sprint.dto;

import com.moyeobom.task.domain.TaskStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public final class ReviewDtos {

    private ReviewDtos() {
    }

    /**
     * @param estimatedSecondsOfDone 예상 시간이 있는 완료 할 일의 예상 합계
     * @param actualSecondsOfDone    예상 시간이 있는 완료 할 일의 실제 합계
     */
    public record ReviewResponse(
            Long sprintId,
            long totalFocusSeconds,
            int completedCount,
            long estimatedSecondsOfDone,
            long actualSecondsOfDone,
            List<ReviewTask> tasks
    ) {
    }

    /**
     * 예상 시간이 없는 할 일은 estimatedSeconds와 diffSeconds가 null이다. diff는 실제 - 예상.
     */
    public record ReviewTask(
            Long taskId,
            String title,
            TaskStatus status,
            Long estimatedSeconds,
            long actualSeconds,
            Long diffSeconds,
            long cumulativeSeconds
    ) {
    }

    public enum CloseAction {
        /** 다음 스프린트로 넘긴다 */
        CARRY,
        /** 미완료로 닫는다 */
        DROP
    }

    /**
     * 미완료 할 일마다 이월 또는 닫기. 보내지 않은 할 일은 이월한다.
     */
    public record SprintCloseRequest(List<@Valid Decision> decisions) {

        public SprintCloseRequest {
            decisions = decisions == null ? List.of() : decisions;
        }
    }

    public record Decision(@NotNull Long taskId, @NotNull CloseAction action) {
    }

    public record SprintCloseResponse(Long sprintId, int carriedCount, int droppedCount, String refreshMessage) {
    }
}
