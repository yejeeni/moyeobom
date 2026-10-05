package com.moyeobom.task.domain;

/**
 * 할 일 추가, 완료, 삭제로 내 자리의 숫자가 바뀌었음을 알린다. 열람실이 커밋 뒤에 받아 반영한다.
 */
public record TaskCountsChangedEvent(Long guestId, TaskCounts counts) {
}
