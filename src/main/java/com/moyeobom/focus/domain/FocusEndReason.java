package com.moyeobom.focus.domain;

public enum FocusEndReason {
    /** 중단 버튼 */
    STOPPED,
    /** 할 일 완료 */
    COMPLETED,
    /** 다른 할 일의 집중을 시작 */
    SWITCHED,
    /** 휴식 버튼 */
    BREAK,
    /** 연결 끊김 또는 서버 재시작 */
    DISCONNECTED,
    /** 오늘 마무리 확정 */
    SPRINT_CLOSED
}
