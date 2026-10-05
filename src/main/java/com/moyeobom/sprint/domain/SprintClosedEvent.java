package com.moyeobom.sprint.domain;

/**
 * 오늘 마무리가 확정됐음을 알린다. 열람실이 커밋 뒤에 받아 방을 정리한다.
 */
public record SprintClosedEvent(Long guestId, Long sprintId) {
}
