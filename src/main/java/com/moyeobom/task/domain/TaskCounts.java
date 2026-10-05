package com.moyeobom.task.domain;

/**
 * 열람실 자리에 표시하는 완료한 일 수와 남은 일 수.
 */
public record TaskCounts(int completedCount, int remainingCount) {

    public static final TaskCounts EMPTY = new TaskCounts(0, 0);

    public int total() {
        return completedCount + remainingCount;
    }
}
