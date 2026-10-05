package com.moyeobom.task.domain;

import com.moyeobom.common.exception.BusinessException;
import com.moyeobom.common.exception.ErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task {

    public static final int TITLE_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long sprintId;

    @Column(nullable = false, length = TITLE_MAX_LENGTH)
    private String title;

    private Integer estimatedMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private TaskStatus status;

    /** 직전 스프린트의 원래 할 일 */
    private Long carriedFromTaskId;

    /** 이월 사슬의 최초 할 일. 처음 만든 할 일은 null */
    private Long rootTaskId;

    @Column(nullable = false)
    private int sortOrder;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime completedAt;

    private Task(Long sprintId, String title, Integer estimatedMinutes, Long carriedFromTaskId, Long rootTaskId,
                 int sortOrder, LocalDateTime now) {
        this.sprintId = sprintId;
        this.title = validTitle(title);
        this.estimatedMinutes = validEstimate(estimatedMinutes);
        this.status = TaskStatus.TODO;
        this.carriedFromTaskId = carriedFromTaskId;
        this.rootTaskId = rootTaskId;
        this.sortOrder = sortOrder;
        this.createdAt = now;
    }

    public static Task create(Long sprintId, String title, Integer estimatedMinutes, int sortOrder,
                              LocalDateTime now) {
        return new Task(sprintId, title, estimatedMinutes, null, null, sortOrder, now);
    }

    /**
     * 이월한 할 일은 새 스프린트에 새 행으로 만든다. 예상 시간은 남은 작업 기준으로 다시 받는다.
     */
    public static Task carryOver(Task from, Long sprintId, Integer estimatedMinutes, int sortOrder,
                                 LocalDateTime now) {
        if (from.status != TaskStatus.CARRIED) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이월하기로 한 할 일만 가져올 수 있습니다.");
        }
        return new Task(sprintId, from.title, estimatedMinutes, from.id, from.rootId(), sortOrder, now);
    }

    public Long rootId() {
        return rootTaskId != null ? rootTaskId : id;
    }

    public void rename(String title) {
        this.title = validTitle(title);
    }

    public void changeEstimate(Integer estimatedMinutes) {
        this.estimatedMinutes = validEstimate(estimatedMinutes);
    }

    public void moveTo(int sortOrder) {
        this.sortOrder = sortOrder;
    }

    public void complete(LocalDateTime now) {
        if (status != TaskStatus.TODO) {
            throw new BusinessException(ErrorCode.TASK_ALREADY_DONE);
        }
        this.status = TaskStatus.DONE;
        this.completedAt = now;
    }

    public void carry() {
        requireTodo();
        this.status = TaskStatus.CARRIED;
    }

    public void drop() {
        requireTodo();
        this.status = TaskStatus.DROPPED;
    }

    public boolean isTodo() {
        return status == TaskStatus.TODO;
    }

    public boolean isDone() {
        return status == TaskStatus.DONE;
    }

    private void requireTodo() {
        if (status != TaskStatus.TODO) {
            throw new IllegalStateException("미완료 할 일만 이월하거나 닫을 수 있습니다: " + id);
        }
    }

    private static String validTitle(String title) {
        String trimmed = title == null ? "" : title.strip();
        if (trimmed.isEmpty() || trimmed.length() > TITLE_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "제목은 1~100자로 입력해 주세요.");
        }
        return trimmed;
    }

    private static Integer validEstimate(Integer estimatedMinutes) {
        if (estimatedMinutes != null && estimatedMinutes <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "예상 시간은 1분 이상이어야 합니다.");
        }
        return estimatedMinutes;
    }
}
