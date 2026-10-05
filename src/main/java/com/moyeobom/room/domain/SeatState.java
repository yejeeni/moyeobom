package com.moyeobom.room.domain;

public enum SeatState {
    FOCUS,
    BREAK,
    /** 실제 유저가 할 일을 고르는 대기 상태. 가상 메이트는 IDLE이 되지 않는다. */
    IDLE
}
