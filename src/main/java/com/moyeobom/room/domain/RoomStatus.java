package com.moyeobom.room.domain;

public enum RoomStatus {
    /** WebSocket이 연결되어 있음 */
    ACTIVE,
    /** 연결 전이거나 끊긴 뒤 다시 연결되기를 기다리는 중. 만료되면 방을 지운다. */
    GRACE
}
