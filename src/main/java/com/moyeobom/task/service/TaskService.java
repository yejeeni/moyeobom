package com.moyeobom.task.service;

import com.moyeobom.common.exception.BusinessException;
import com.moyeobom.common.exception.ErrorCode;
import com.moyeobom.common.message.RefreshMessages;
import com.moyeobom.common.time.Times;
import com.moyeobom.focus.service.FocusQueryService;
import com.moyeobom.focus.service.FocusService;
import com.moyeobom.sprint.domain.Sprint;
import com.moyeobom.sprint.service.SprintQueryService;
import com.moyeobom.task.domain.Task;
import com.moyeobom.task.domain.TaskCountsChangedEvent;
import com.moyeobom.task.dto.TaskDtos.CarriedTaskRequest;
import com.moyeobom.task.dto.TaskDtos.TaskCompleteResponse;
import com.moyeobom.task.dto.TaskDtos.TaskCreateRequest;
import com.moyeobom.task.dto.TaskDtos.TaskResponse;
import com.moyeobom.task.dto.TaskDtos.TaskUpdateRequest;
import com.moyeobom.task.repository.TaskRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final TaskQueryService taskQueryService;
    private final SprintQueryService sprintQueryService;
    private final FocusQueryService focusQueryService;
    private final FocusService focusService;
    private final RefreshMessages refreshMessages;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /**
     * 스프린트를 시작할 때 이월 할 일을 먼저, 새 할 일을 그 뒤에 만든다.
     */
    @Transactional
    public void createSprintTasks(Long guestId, Long sprintId, List<TaskCreateRequest> newTasks,
                                  List<CarriedTaskRequest> carriedTasks) {
        LocalDateTime now = Times.now(clock);
        int sortOrder = 0;
        Set<Long> carriedIds = new HashSet<>();
        for (CarriedTaskRequest carried : carriedTasks) {
            if (!carriedIds.add(carried.fromTaskId())) {
                throw new BusinessException(ErrorCode.TASK_ALREADY_CARRIED);
            }
            Task from = findCarryable(guestId, carried.fromTaskId());
            try {
                taskRepository.saveAndFlush(
                        Task.carryOver(from, sprintId, carried.estimatedMinutes(), ++sortOrder, now));
            } catch (DataIntegrityViolationException e) {
                // 동시에 같은 할 일을 이월하면 carried_from_task_id UNIQUE가 막는다
                throw new BusinessException(ErrorCode.TASK_ALREADY_CARRIED);
            }
        }
        for (TaskCreateRequest task : newTasks) {
            taskRepository.save(Task.create(sprintId, task.title(), task.estimatedMinutes(), ++sortOrder, now));
        }
        publishCounts(guestId, sprintId);
    }

    @Transactional
    public TaskResponse add(Long guestId, TaskCreateRequest request) {
        Sprint sprint = sprintQueryService.getOpenSprint(guestId);
        int sortOrder = taskRepository.findMaxSortOrder(sprint.getId()) + 1;
        Task task = taskRepository.saveAndFlush(Task.create(sprint.getId(), request.title(), request.estimatedMinutes(),
                sortOrder, Times.now(clock)));
        publishCounts(guestId, sprint.getId());
        return taskQueryService.toResponse(task);
    }

    @Transactional
    public TaskResponse update(Long guestId, Long taskId, TaskUpdateRequest request) {
        Task task = taskQueryService.getTaskInOpenSprint(guestId, taskId);
        if (request.title() != null) {
            task.rename(request.title());
        }
        if (request.estimatedMinutes() != null) {
            task.changeEstimate(request.estimatedMinutes());
        }
        if (request.sortOrder() != null) {
            task.moveTo(request.sortOrder());
        }
        return taskQueryService.toResponse(task);
    }

    @Transactional
    public void delete(Long guestId, Long taskId) {
        Task task = taskQueryService.getTaskInOpenSprint(guestId, taskId);
        if (focusQueryService.hasRecords(task.getId())) {
            throw new BusinessException(ErrorCode.TASK_HAS_RECORDS);
        }
        taskRepository.delete(task);
        taskRepository.flush();
        publishCounts(guestId, task.getSprintId());
    }

    @Transactional
    public TaskCompleteResponse complete(Long guestId, Long taskId) {
        sprintQueryService.lockOpenSprint(guestId);
        Task task = taskQueryService.getTaskInOpenSprint(guestId, taskId);
        task.complete(Times.now(clock));
        focusService.endIfFocusingOn(guestId, taskId);
        taskRepository.flush();
        publishCounts(guestId, task.getSprintId());
        return new TaskCompleteResponse(taskQueryService.toResponse(task), refreshMessages.pick());
    }

    private Task findCarryable(Long guestId, Long fromTaskId) {
        Task from = taskRepository.findOwnedTask(fromTaskId, guestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        if (taskRepository.existsByCarriedFromTaskId(from.getId())) {
            throw new BusinessException(ErrorCode.TASK_ALREADY_CARRIED);
        }
        return from;
    }

    private void publishCounts(Long guestId, Long sprintId) {
        eventPublisher.publishEvent(new TaskCountsChangedEvent(guestId, taskQueryService.countsOfSprint(sprintId)));
    }
}
