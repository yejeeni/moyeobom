package com.moyeobom.focus.service;

import com.moyeobom.common.exception.BusinessException;
import com.moyeobom.common.exception.ErrorCode;
import com.moyeobom.common.time.Times;
import com.moyeobom.focus.domain.FocusEndReason;
import com.moyeobom.focus.domain.FocusSession;
import com.moyeobom.focus.domain.MyState;
import com.moyeobom.focus.domain.MyStateChangedEvent;
import com.moyeobom.focus.dto.FocusDtos.MyStatusResponse;
import com.moyeobom.focus.dto.FocusDtos.SessionView;
import com.moyeobom.focus.dto.FocusDtos.StopReason;
import com.moyeobom.focus.repository.FocusSessionRepository;
import com.moyeobom.room.domain.SeatState;
import com.moyeobom.sprint.service.SprintQueryService;
import com.moyeobom.task.domain.Task;
import com.moyeobom.task.service.TaskQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 집중 세션의 시작과 종료. 같은 게스트의 요청은 열린 스프린트 행 잠금으로 한 줄로 세운다.
 * 휴식과 대기 상태는 DB에 남기지 않고 메모리에만 둔다. 서버가 재시작되면 대기 상태로 돌아간다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FocusService {

    private final FocusSessionRepository focusSessionRepository;
    private final SprintQueryService sprintQueryService;
    private final TaskQueryService taskQueryService;
    private final ApplicationEventPublisher eventPublisher;
    private final Clock clock;

    /** 집중 중이 아닐 때의 상태(휴식, 대기)와 그 시각 */
    private final Map<Long, MyState> idleStates = new ConcurrentHashMap<>();

    /**
     * 집중을 시작한다. 다른 할 일의 세션이 진행 중이면 먼저 끝내고, 같은 할 일이면 그대로 둔다.
     */
    @Transactional
    public MyStatusResponse start(Long guestId, Long taskId) {
        sprintQueryService.lockOpenSprint(guestId);
        Task task = taskQueryService.getTaskInOpenSprint(guestId, taskId);
        if (!task.isTodo()) {
            throw new BusinessException(ErrorCode.TASK_ALREADY_DONE);
        }
        LocalDateTime now = Times.now(clock);
        Optional<FocusSession> running = findRunning(guestId);
        if (running.isPresent() && running.get().getTaskId().equals(taskId)) {
            return toStatus(running.get(), now);
        }
        running.ifPresent(session -> end(session, FocusEndReason.SWITCHED, now));

        FocusSession session = focusSessionRepository.save(FocusSession.start(taskId, now));
        idleStates.remove(guestId);
        eventPublisher.publishEvent(new MyStateChangedEvent(guestId,
                new MyState(SeatState.FOCUS, Times.toInstant(now)), session.getId()));
        return toStatus(session, now);
    }

    /**
     * 중단하면 대기, 휴식이면 휴식 상태가 된다. 휴식 시간은 할 일 기록에 들어가지 않는다.
     */
    @Transactional
    public MyStatusResponse stop(Long guestId, StopReason reason) {
        sprintQueryService.findOpenSprintForUpdate(guestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOCUS_NOT_RUNNING));
        FocusSession session = findRunning(guestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FOCUS_NOT_RUNNING));
        LocalDateTime now = Times.now(clock);
        boolean isBreak = reason == StopReason.BREAK;
        end(session, isBreak ? FocusEndReason.BREAK : FocusEndReason.STOPPED, now);
        changeIdleState(guestId, isBreak ? SeatState.BREAK : SeatState.IDLE, now);
        return statusOf(guestId, now);
    }

    /**
     * 할 일을 완료할 때 그 할 일에 집중 중이었다면 세션을 끝내고 대기 상태로 바꾼다.
     * 호출한 쪽이 열린 스프린트를 잠근 트랜잭션 안에서 부른다.
     */
    @Transactional
    public void endIfFocusingOn(Long guestId, Long taskId) {
        findRunning(guestId)
                .filter(session -> session.getTaskId().equals(taskId))
                .ifPresent(session -> {
                    LocalDateTime now = Times.now(clock);
                    end(session, FocusEndReason.COMPLETED, now);
                    changeIdleState(guestId, SeatState.IDLE, now);
                });
    }

    /**
     * 스프린트를 마무리할 때 진행 중인 세션을 닫고 대기 상태로 바꾼다.
     */
    @Transactional
    public void endForSprintClose(Long guestId) {
        LocalDateTime now = Times.now(clock);
        findRunning(guestId).ifPresent(session -> end(session, FocusEndReason.SPRINT_CLOSED, now));
        changeIdleState(guestId, SeatState.IDLE, now);
    }

    /**
     * 연결이 끊기면 마지막 신호 시각을 종료 시각으로 세션을 닫는다.
     */
    @Transactional
    public void endForDisconnect(Long guestId, Instant lastSignalAt) {
        Optional<FocusSession> running = findRunning(guestId);
        running.ifPresent(session -> end(session, FocusEndReason.DISCONNECTED, Times.toUtc(lastSignalAt)));
        if (running.isPresent() || idleStates.containsKey(guestId)) {
            changeIdleState(guestId, SeatState.IDLE, Times.now(clock));
        }
    }

    /**
     * 확인 신호가 threshold보다 오래된 세션을 마지막 신호 시각으로 닫는다.
     * 서버 재시작 직후에는 모든 진행 중 세션이 대상이다.
     */
    @Transactional
    public int endStaleSessions(LocalDateTime threshold) {
        List<FocusSession> stale = focusSessionRepository.findRunningHeartbeatBefore(threshold);
        stale.forEach(session -> end(session, FocusEndReason.DISCONNECTED, session.getLastHeartbeatAt()));
        return stale.size();
    }

    @Transactional
    public void touchHeartbeat(Collection<Long> guestIds) {
        if (!guestIds.isEmpty()) {
            focusSessionRepository.touchHeartbeat(guestIds, Times.now(clock));
        }
    }

    @Transactional(readOnly = true)
    public MyStatusResponse getStatus(Long guestId) {
        return statusOf(guestId, Times.now(clock));
    }

    @Transactional(readOnly = true)
    public MyState getMyState(Long guestId) {
        LocalDateTime now = Times.now(clock);
        return findRunning(guestId)
                .map(session -> new MyState(SeatState.FOCUS, Times.toInstant(session.getStartedAt())))
                .orElseGet(() -> idleStates.getOrDefault(guestId, new MyState(SeatState.IDLE, Times.toInstant(now))));
    }

    @Transactional(readOnly = true)
    public Optional<Long> findRunningSessionId(Long guestId) {
        return findRunning(guestId).map(FocusSession::getId);
    }

    private MyStatusResponse statusOf(Long guestId, LocalDateTime now) {
        return findRunning(guestId)
                .map(session -> toStatus(session, now))
                .orElseGet(() -> {
                    MyState state = getMyState(guestId);
                    return new MyStatusResponse(state.state(), state.since(), Times.toInstant(now), null);
                });
    }

    private MyStatusResponse toStatus(FocusSession session, LocalDateTime now) {
        Instant startedAt = Times.toInstant(session.getStartedAt());
        return new MyStatusResponse(SeatState.FOCUS, startedAt, Times.toInstant(now),
                new SessionView(session.getId(), session.getTaskId(), startedAt, session.elapsedSeconds(now)));
    }

    private Optional<FocusSession> findRunning(Long guestId) {
        List<FocusSession> running = focusSessionRepository.findRunningByGuestId(guestId);
        if (running.size() > 1) {
            log.warn("진행 중 세션이 여러 개입니다. guestId={}, count={}", guestId, running.size());
        }
        return running.stream().findFirst();
    }

    private void end(FocusSession session, FocusEndReason reason, LocalDateTime endedAt) {
        session.end(reason, endedAt);
        if (session.isTooShort()) {
            focusSessionRepository.delete(session);
        }
        focusSessionRepository.flush();
    }

    private void changeIdleState(Long guestId, SeatState state, LocalDateTime now) {
        MyState myState = new MyState(state, Times.toInstant(now));
        idleStates.put(guestId, myState);
        eventPublisher.publishEvent(new MyStateChangedEvent(guestId, myState, null));
    }
}
