package com.moyeobom.sprint.service;

import com.moyeobom.common.exception.BusinessException;
import com.moyeobom.common.exception.ErrorCode;
import com.moyeobom.common.time.Times;
import com.moyeobom.sprint.domain.Sprint;
import com.moyeobom.sprint.dto.SprintDtos.CarryoverResponse;
import com.moyeobom.sprint.dto.SprintDtos.CarryoverTask;
import com.moyeobom.sprint.dto.SprintDtos.CurrentSprintResponse;
import com.moyeobom.sprint.dto.SprintDtos.SprintStartRequest;
import com.moyeobom.sprint.dto.SprintDtos.SprintView;
import com.moyeobom.sprint.repository.SprintRepository;
import com.moyeobom.task.domain.Task;
import com.moyeobom.task.dto.TaskDtos.TaskTimes;
import com.moyeobom.task.service.TaskQueryService;
import com.moyeobom.task.service.TaskService;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SprintService {

    private final SprintRepository sprintRepository;
    private final SprintQueryService sprintQueryService;
    private final TaskService taskService;
    private final TaskQueryService taskQueryService;
    private final Clock clock;

    @Transactional
    public CurrentSprintResponse start(Long guestId, SprintStartRequest request) {
        if (request.tasks().isEmpty() && request.carriedTasks().isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "할 일을 하나 이상 적어 주세요.");
        }
        if (sprintQueryService.hasOpenSprint(guestId)) {
            throw new BusinessException(ErrorCode.SPRINT_ALREADY_OPEN);
        }
        Sprint sprint;
        try {
            sprint = sprintRepository.saveAndFlush(Sprint.open(guestId, Times.now(clock)));
        } catch (DataIntegrityViolationException e) {
            // 동시에 두 번 시작하면 open_guest_id UNIQUE가 막는다
            throw new BusinessException(ErrorCode.SPRINT_ALREADY_OPEN);
        }
        taskService.createSprintTasks(guestId, sprint.getId(), request.tasks(), request.carriedTasks());
        return new CurrentSprintResponse(toView(sprint));
    }

    @Transactional(readOnly = true)
    public CurrentSprintResponse getCurrent(Long guestId) {
        return new CurrentSprintResponse(sprintQueryService.findOpenSprint(guestId).map(this::toView).orElse(null));
    }

    @Transactional(readOnly = true)
    public CarryoverResponse getCarryover(Long guestId) {
        List<Task> candidates = taskQueryService.findCarryoverCandidates(guestId);
        if (candidates.isEmpty()) {
            return new CarryoverResponse(List.of());
        }
        Map<Long, TaskTimes> times = taskQueryService.findTimes(candidates.getFirst().getSprintId());
        return new CarryoverResponse(candidates.stream()
                .map(task -> new CarryoverTask(task.getId(), task.getTitle(), task.getEstimatedMinutes(),
                        times.getOrDefault(task.getId(), TaskTimes.ZERO).cumulativeSeconds()))
                .toList());
    }

    private SprintView toView(Sprint sprint) {
        return new SprintView(sprint.getId(), Times.toInstant(sprint.getStartedAt()),
                taskQueryService.findTaskResponses(sprint.getId()));
    }
}
