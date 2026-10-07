package com.moyeobom.room.domain;

/**
 * 자리 종류. 서버 안에서만 쓴다. 클라이언트에는 비어 있는 실제 자리만 '초대 대기'로 알린다.
 */
public enum SeatType {
    /** 코드로 들어온 실제 사람이 앉는 자리 */
    REAL,
    /** 가상 메이트가 앉는 자리 */
    VIRTUAL
}
