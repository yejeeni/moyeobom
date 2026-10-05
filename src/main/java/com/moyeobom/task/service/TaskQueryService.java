package com.moyeobom.task.service;

import com.moyeobom.common.exception.BusinessException;
import com.moyeobom.common.exception.ErrorCode;
import com.moyeobom.sprint.domain.Sprint;
import com.moyeobom.sprint.service.SprintQueryService;
import com.moyeobom.task.domain.Task;
import com.moyeobom.task.domain.TaskCounts;
import com.moyeobom.task.domain.TaskStatus;
import com.moyeobom.task.dto.TaskDtos.TaskResponse;
import com.moyeobom.task.dto.TaskDtos.TaskTimes;
import com.moyeobom.task.repository.TaskRepository;
import com.moyeobom.task.repository.TaskTimeView;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 다른 기능(집중, 열람실, 스프린트)이 할 일을 확인할 때 쓰는 조회 전용 서비스.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TaskQueryService {

    private final TaskRepository taskRepository;
    private final SprintQueryService sprintQueryService;

    /**
     * 열린 스프린트에 있는 내 할 일. 다른 게스트의 할 일이나 닫힌 스프린트의 할 일은 404로 숨긴다.
     */
    public Task getTaskInOpenSprint(Long guestId, Long taskId) {
        Task task = taskRepository.findOwnedTask(taskId, guestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.TASK_NOT_FOUND));
        boolean inOpenSprint = sprintQueryService.findOpenSprint(guestId)
                .map(sprint -> sprint.getId().equals(task.getSprintId()))
                .orElse(false);
        if (!inOpenSprint) {
            throw new BusinessException(ErrorCode.TASK_NOT_FOUND);
        }
        return task;
    }

    public TaskCounts countsOf(Long guestId) {
        return sprintQueryService.findOpenSprint(guestId)
                .map(sprint -> countsOfSprint(sprint.getId()))
                .orElse(TaskCounts.EMPTY);
    }

    public TaskCounts countsOfSprint(Long sprintId) {
        int done = (int) taskRepository.countBySprintIdAndStatus(sprintId, TaskStatus.DONE);
        int todo = (int) taskRepository.countBySprintIdAndStatus(sprintId, TaskStatus.TODO);
        return new TaskCounts(done, todo);
    }

    public List<Task> findTasks(Long sprintId) {
        return taskRepository.findBySprintIdOrderBySortOrderAscIdAsc(sprintId);
    }

    public List<TaskResponse> findTaskResponses(Long sprintId) {
        Map<Long, TaskTimes> times = findTimes(sprintId);
        return findTasks(sprintId).stream()
                .map(task -> TaskResponse.of(task, times.getOrDefault(task.getId(), TaskTimes.ZERO)))
                .toList();
    }

    public TaskResponse toResponse(Task task) {
        return TaskResponse.of(task, findTimes(task.getSprintId()).getOrDefault(task.getId(), TaskTimes.ZERO));
    }

    public Map<Long, TaskTimes> findTimes(Long sprintId) {
        return taskRepository.findTimesBySprintId(sprintId).stream()
                .collect(Collectors.toMap(view -> view.getTaskId().longValue(), TaskQueryService::toTimes));
    }

    /**
     * 마지막으로 닫힌 스프린트에서 이월하기로 했고 아직 가져가지 않은 할 일.
     */
    public List<Task> findCarryoverCandidates(Long guestId) {
        return sprintQueryService.findLastClosedSprint(guestId)
                .map(Sprint::getId)
                .map(taskRepository::findCarryoverCandidates)
                .orElse(List.of());
    }

    private static TaskTimes toTimes(TaskTimeView view) {
        return new TaskTimes(view.getActualSeconds().longValue(), view.getCumulativeSeconds().longValue(),
                view.getSessionCount().longValue());
    }
}
