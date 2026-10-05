package com.moyeobom.focus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.moyeobom.focus.domain.FocusEndReason;
import com.moyeobom.focus.domain.FocusSession;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class FocusSessionTest {

    static final LocalDateTime START = LocalDateTime.of(2026, 10, 5, 9, 0);

    @Test
    void 끝나면_초_단위_기간이_확정된다() {
        FocusSession session = FocusSession.start(1L, START);

        session.end(FocusEndReason.STOPPED, START.plusMinutes(42).plusSeconds(5));

        assertThat(session.isRunning()).isFalse();
        assertThat(session.getDurationSeconds()).isEqualTo(42 * 60 + 5);
        assertThat(session.isTooShort()).isFalse();
    }

    @Test
    void 육십초_미만이면_저장하지_않을_세션이다() {
        FocusSession under = FocusSession.start(1L, START);
        under.end(FocusEndReason.BREAK, START.plusSeconds(59));
        FocusSession exact = FocusSession.start(1L, START);
        exact.end(FocusEndReason.BREAK, START.plusSeconds(60));

        assertThat(under.isTooShort()).isTrue();
        assertThat(exact.isTooShort()).isFalse();
    }

    @Test
    void 종료_시각이_시작보다_앞서면_시작_시각으로_맞춘다() {
        FocusSession session = FocusSession.start(1L, START);

        session.end(FocusEndReason.DISCONNECTED, START.minusSeconds(5));

        assertThat(session.getEndedAt()).isEqualTo(START);
        assertThat(session.getDurationSeconds()).isZero();
    }

    @Test
    void 이미_끝난_세션은_다시_끝낼_수_없다() {
        FocusSession session = FocusSession.start(1L, START);
        session.end(FocusEndReason.STOPPED, START.plusMinutes(5));

        assertThatThrownBy(() -> session.end(FocusEndReason.STOPPED, START.plusMinutes(6)))
                .isInstanceOf(IllegalStateException.class);
    }
}
