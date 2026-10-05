package com.moyeobom.focus.repository;

import com.moyeobom.focus.domain.FocusSession;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface FocusSessionRepository extends JpaRepository<FocusSession, Long> {

    boolean existsByTaskId(Long taskId);

    @Query("""
            select f from FocusSession f, Task t, Sprint s
            where f.taskId = t.id and t.sprintId = s.id
              and s.guestId = :guestId and f.endedAt is null
            order by f.startedAt desc
            """)
    List<FocusSession> findRunningByGuestId(Long guestId);

    /**
     * 마지막 확인 신호가 기준보다 오래된 진행 중 세션. (ended_at, last_heartbeat_at) 인덱스를 쓴다.
     */
    @Query("select f from FocusSession f where f.endedAt is null and f.lastHeartbeatAt < :threshold")
    List<FocusSession> findRunningHeartbeatBefore(LocalDateTime threshold);

    /**
     * 연결된 게스트의 진행 중 세션에 확인 신호 시각을 1분마다 한꺼번에 기록한다.
     */
    @Modifying
    @Query(value = """
            UPDATE focus_session f
            JOIN task t ON t.id = f.task_id
            JOIN sprint s ON s.id = t.sprint_id
            SET f.last_heartbeat_at = :now
            WHERE f.ended_at IS NULL AND s.guest_id IN (:guestIds)
            """, nativeQuery = true)
    int touchHeartbeat(Collection<Long> guestIds, LocalDateTime now);
}
