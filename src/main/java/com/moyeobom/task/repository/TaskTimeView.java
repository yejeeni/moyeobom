package com.moyeobom.task.repository;

/**
 * 할 일별 집중 시간 집계. MySQL SUM이 DECIMAL을 돌려주므로 Number로 받는다.
 */
public interface TaskTimeView {

    Number getTaskId();

    /** 이 할 일(이번 스프린트)에 쌓인 시간 */
    Number getActualSeconds();

    /** 이월 사슬 전체에 쌓인 시간 */
    Number getCumulativeSeconds();

    /** 진행 중인 세션을 포함한 집중 기록 수 */
    Number getSessionCount();
}
