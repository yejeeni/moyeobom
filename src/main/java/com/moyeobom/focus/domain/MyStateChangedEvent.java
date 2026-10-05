package com.moyeobom.focus.domain;

/**
 * 집중 시작, 휴식, 중단, 완료로 내 상태가 바뀌었음을 알린다. 커밋 뒤에 열람실과 휴식 알림이 받는다.
 *
 * @param sessionId 집중을 시작한 경우 그 세션 id, 아니면 null
 */
public record MyStateChangedEvent(Long guestId, MyState myState, Long sessionId) {
}
