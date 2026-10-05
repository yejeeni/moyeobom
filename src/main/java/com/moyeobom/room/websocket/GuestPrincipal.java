package com.moyeobom.room.websocket;

import java.security.Principal;

/**
 * STOMP 세션의 사용자. 이름은 내부 게스트 id로, /user 주소를 이 값으로 찾는다. 클라이언트에는 노출되지 않는다.
 */
public record GuestPrincipal(Long guestId) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(guestId);
    }
}
