package com.moyeobom.sprint.service;

import com.moyeobom.common.exception.BusinessException;
import com.moyeobom.common.exception.ErrorCode;
import com.moyeobom.common.message.RefreshMessages;
import com.moyeobom.common.time.Times;
import com.moyeobom.focus.service.FocusService;
import com.moyeobom.sprint.domain.Sprint;
import com.moyeobom.sprint.domain.SprintClosedEvent;
import com.moyeobom.sprint.dto.ReviewDtos.CloseAction;
import com.moyeobom.sprint.dto.ReviewDtos.Decision;
import com.moyeobom.sprint.dto.ReviewDtos.ReviewResponse;
import com.moyeobom.sprint.dto.ReviewDtos.ReviewTask;
import com.moyeobom.sprint.dto.ReviewDtos.SprintCloseRequest;
import com.moyeobom.sprint.dto.ReviewDtos.SprintCloseResponse;
import com.moyeobom.task.domain.Task;
import com.moyeobom.task.dto.TaskDtos.TaskTimes;
import com.moyeobom.task.service.TaskQueryService;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 스프린트 회고와 마무리. 회고는 이번 스프린트에 쌓인 시간만 비교하고, 이월 포함 누적 시간은 따로 보여준다.
 */
@Service
@RequiredArgsConstructor
public class ReviewService {

    private final SprintQueryService sprintQueryService;
    private final TaskQueryService taskQueryService;
    private final FocusService focusService;
    private final RefreshMessages refreshMessages;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    @Transactional(readOnly = true)
    public ReviewResponse getReview(Long guestId) {
        Sprint sprint = sprintQueryService.getOpenSprint(guestId);
        List<Task> tasks = taskQueryService.findTasks(sprint.getId());
        Map<Long, TaskTimes> times = taskQueryService.findTimes(sprint.getId());
        return review(sprint, tasks, times);
    }

    static ReviewResponse review(Sprint sprint, List<Task> tasks, Map<Long, TaskTimes> times) {
        long totalFocusSeconds = 0;
        int completedCount = 0;
        long estimatedOfDone = 0;
        long actualOfDone = 0;
        List<ReviewTask> reviewTasks = new ArrayList<>();
        for (Task task : tasks) {
            TaskTimes time = times.getOrDefault(task.getId(), TaskTimes.ZERO);
            Long estimatedSeconds = task.getEstimatedMinutes() == null ? null : task.getEstimatedMinutes() * 60L;
            Long diffSeconds = estimatedSeconds == null ? null : time.actualSeconds() - estimatedSeconds;

            totalFocusSeconds += time.actualSeconds();
            if (task.isDone()) {
                completedCount++;
                if (estimatedSeconds != null) {
                    estimatedOfDone += estimatedSeconds;
                    actualOfDone += time.actualSeconds();
                }
            }
            reviewTasks.add(new ReviewTask(task.getId(), task.getTitle(), task.getStatus(), estimatedSeconds,
                    time.actualSeconds(), diffSeconds, time.cumulativeSeconds()));
        }
        return new ReviewResponse(sprint.getId(), totalFocusSeconds, completedCount, estimatedOfDone, actualOfDone,
                reviewTasks);
    }

    /**
     * 진행 중인 세션을 SPRINT_CLOSED로 닫고, 미완료 할 일을 이월하거나 닫은 뒤 스프린트를 닫는다.
     * 열람실 퇴장은 커밋 뒤에 이벤트로 처리한다.
     */
    @Transactional
    public SprintCloseResponse close(Long guestId, SprintCloseRequest request) {
        Sprint sprint = sprintQueryService.lockOpenSprint(guestId);
        List<Task> todo = taskQueryService.findTasks(sprint.getId()).stream().filter(Task::isTodo).toList();
        Map<Long, CloseAction> actions = decisionsOf(request, todo);

        focusService.endForSprintClose(guestId);
        int carried = 0;
        int dropped = 0;
        for (Task task : todo) {
            if (actions.getOrDefault(task.getId(), CloseAction.CARRY) == CloseAction.CARRY) {
                task.carry();
                carried++;
            } else {
                task.drop();
                dropped++;
            }
        }
        sprint.close(Times.now(clock));
        eventPublisher.publishEvent(new SprintClosedEvent(guestId, sprint.getId()));
        return new SprintCloseResponse(sprint.getId(), carried, dropped, refreshMessages.pick());
    }

    private static Map<Long, CloseAction> decisionsOf(SprintCloseRequest request, List<Task> todo) {
        Map<Long, CloseAction> actions = new HashMap<>();
        for (Decision decision : request.decisions()) {
            boolean isTodo = todo.stream().anyMatch(task -> task.getId().equals(decision.taskId()));
            if (!isTodo) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "이번 스프린트의 미완료 할 일만 고를 수 있습니다: " + decision.taskId());
            }
            if (actions.put(decision.taskId(), decision.action()) != null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "같은 할 일을 두 번 골랐습니다: " + decision.taskId());
            }
        }
        return actions;
    }
}
