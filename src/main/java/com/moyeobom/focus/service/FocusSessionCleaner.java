package com.moyeobom.focus.service;

import com.moyeobom.common.time.Times;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 닫히지 않은 집중 세션을 정리한다.
 * <ul>
 *   <li>서버가 다시 뜨면 진행 중이던 세션을 모두 마지막 확인 신호 시각으로 닫는다.</li>
 *   <li>연결된 게스트의 세션은 1분마다 신호 시각이 갱신되므로, 그보다 오래 갱신되지 않은 세션은 연결이 없는 것으로 보고 닫는다.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FocusSessionCleaner {

    static final Duration STALE_AFTER = Duration.ofMinutes(3);

    private final FocusService focusService;
    private final Clock clock;

    @EventListener(ApplicationReadyEvent.class)
    public void closeSessionsLeftByRestart() {
        int closed = focusService.endStaleSessions(LocalDateTime.of(9999, 12, 31, 0, 0));
        if (closed > 0) {
            log.info("재시작 전에 열려 있던 집중 세션 {}개를 닫았습니다.", closed);
        }
    }

    @Scheduled(fixedDelayString = "${moyeobom.focus.stale-check-interval:PT1M}",
            initialDelayString = "${moyeobom.focus.stale-check-interval:PT1M}")
    public void closeStaleSessions() {
        int closed = focusService.endStaleSessions(Times.now(clock).minus(STALE_AFTER));
        if (closed > 0) {
            log.info("확인 신호가 끊긴 집중 세션 {}개를 닫았습니다.", closed);
        }
    }
}
