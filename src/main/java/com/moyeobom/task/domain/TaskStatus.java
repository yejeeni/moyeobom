package com.moyeobom.task.domain;

public enum TaskStatus {
    TODO,
    DONE,
    /** 스프린트를 마무리하며 다음 스프린트로 넘기기로 한 할 일 */
    CARRIED,
    /** 스프린트를 마무리하며 미완료로 닫은 할 일 */
    DROPPED
}
