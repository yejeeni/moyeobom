package com.moyeobom.room.domain;

/**
 * 서버 안에서만 쓰고 이벤트에는 넣지 않는다. 클라이언트는 실제 유저와 가상 메이트를 구분할 수 없다.
 */
public enum OccupantKind {
    REAL,
    VIRTUAL
}
