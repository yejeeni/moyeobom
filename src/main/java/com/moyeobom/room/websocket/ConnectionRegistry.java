package com.moyeobom.room.websocket;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * 연결된 STOMP 세션과 마지막 신호 시각. 10초마다 오는 연결 확인 신호는 DB에 쓰지 않고 여기서만 추적한다.
 */
@Component
public class ConnectionRegistry {

    private final Map<String, Long> guestBySession = new ConcurrentHashMap<>();
    private final Map<String, Instant> lastSignalBySession = new ConcurrentHashMap<>();
    private final Map<Long, Set<String>> sessionsByGuest = new ConcurrentHashMap<>();

    public void connected(String sessionId, Long guestId, Instant now) {
        guestBySession.put(sessionId, guestId);
        lastSignalBySession.put(sessionId, now);
        sessionsByGuest.computeIfAbsent(guestId, id -> ConcurrentHashMap.newKeySet()).add(sessionId);
    }

    public void touch(String sessionId, Instant now) {
        lastSignalBySession.computeIfPresent(sessionId, (id, previous) -> now);
    }

    /**
     * 세션을 지운다. 그 게스트의 마지막 연결이었으면 끊김 정보를 돌려준다.
     */
    public Optional<Disconnection> disconnected(String sessionId) {
        Long guestId = guestBySession.remove(sessionId);
        Instant lastSignal = lastSignalBySession.remove(sessionId);
        if (guestId == null) {
            return Optional.empty();
        }
        Set<String> remaining = sessionsByGuest.computeIfPresent(guestId, (id, sessions) -> {
            sessions.remove(sessionId);
            return sessions.isEmpty() ? null : sessions;
        });
        if (remaining != null) {
            return Optional.empty();
        }
        return Optional.of(new Disconnection(guestId, lastSignal));
    }

    public boolean isConnected(Long guestId) {
        return sessionsByGuest.containsKey(guestId);
    }

    public Set<Long> connectedGuestIds() {
        return Set.copyOf(sessionsByGuest.keySet());
    }

    public record Disconnection(Long guestId, Instant lastSignalAt) {
    }
}
