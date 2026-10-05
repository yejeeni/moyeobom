package com.moyeobom.task.repository;

import com.moyeobom.task.domain.Task;
import com.moyeobom.task.domain.TaskStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TaskRepository extends JpaRepository<Task, Long> {

    List<Task> findBySprintIdOrderBySortOrderAscIdAsc(Long sprintId);

    long countBySprintIdAndStatus(Long sprintId, TaskStatus status);

    @Query("select coalesce(max(t.sortOrder), 0) from Task t where t.sprintId = :sprintId")
    int findMaxSortOrder(Long sprintId);

    boolean existsByCarriedFromTaskId(Long carriedFromTaskId);

    /**
     * 게스트의 할 일만 찾는다. 다른 게스트의 할 일은 없는 것처럼 다룬다.
     */
    @Query("""
            select t from Task t, Sprint s
            where t.sprintId = s.id and t.id = :taskId and s.guestId = :guestId
            """)
    Optional<Task> findOwnedTask(Long taskId, Long guestId);

    /**
     * 이월하기로 했지만 아직 다음 스프린트로 가져가지 않은 할 일.
     */
    @Query("""
            select t from Task t
            where t.sprintId = :sprintId
              and t.status = com.moyeobom.task.domain.TaskStatus.CARRIED
              and not exists (select 1 from Task n where n.carriedFromTaskId = t.id)
            order by t.sortOrder, t.id
            """)
    List<Task> findCarryoverCandidates(Long sprintId);

    /**
     * 스프린트의 할 일별 실제 시간과 이월 포함 누적 시간을 한 번에 구한다.
     * 누적 시간은 root_task_id가 같은 할 일들의 세션을 합쳐 재귀 없이 계산한다.
     */
    @Query(value = """
            SELECT t.id AS taskId,
                   COALESCE((SELECT SUM(f.duration_seconds) FROM focus_session f
                             WHERE f.task_id = t.id), 0) AS actualSeconds,
                   COALESCE((SELECT SUM(f.duration_seconds) FROM focus_session f
                             JOIN task c ON c.id = f.task_id
                             WHERE c.id = COALESCE(t.root_task_id, t.id)
                                OR c.root_task_id = COALESCE(t.root_task_id, t.id)), 0) AS cumulativeSeconds,
                   (SELECT COUNT(*) FROM focus_session f WHERE f.task_id = t.id) AS sessionCount
            FROM task t
            WHERE t.sprint_id = :sprintId
            """, nativeQuery = true)
    List<TaskTimeView> findTimesBySprintId(Long sprintId);
}
