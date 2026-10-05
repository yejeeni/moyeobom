package com.moyeobom.focus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.moyeobom.common.message.RefreshMessages;
import com.moyeobom.focus.domain.MyState;
import com.moyeobom.focus.domain.MyStateChangedEvent;
import com.moyeobom.focus.service.BreakAlertScheduler;
import com.moyeobom.focus.service.FocusService;
import com.moyeobom.guest.dto.GuestDtos.SettingResponse;
import com.moyeobom.guest.service.GuestService;
import com.moyeobom.room.domain.SeatState;
import com.moyeobom.room.dto.RoomDtos.RoomEventType;
import com.moyeobom.room.service.RoomService;
import com.moyeobom.support.FakeTaskScheduler;
import com.moyeobom.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BreakAlertSchedulerTest {

    static final Instant START = Instant.parse("2026-10-05T09:00:00Z");
    static final Long GUEST = 1L;
    static final Long SESSION = 10L;

    MutableClock clock;
    FakeTaskScheduler scheduler;
    GuestService guestService;
    FocusService focusService;
    RoomService roomService;
    BreakAlertScheduler breakAlert;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(START);
        scheduler = new FakeTaskScheduler(clock);
        guestService = mock(GuestService.class);
        focusService = mock(FocusService.class);
        roomService = mock(RoomService.class);
        breakAlert = new BreakAlertScheduler(guestService, focusService, roomService,
                new RefreshMessages(new Random(1)), scheduler);
    }

    @Test
    void 알림을_켜면_설정한_시간만큼_집중했을_때_한_번_알린다() {
        when(guestService.getSetting(GUEST)).thenReturn(new SettingResponse(true, 50));
        when(focusService.findRunningSessionId(GUEST)).thenReturn(Optional.of(SESSION));

        breakAlert.onMyStateChanged(focusStarted());

        assertThat(scheduler.runNext(START.plus(Duration.ofMinutes(49)))).isFalse();
        assertThat(scheduler.runNext(START.plus(Duration.ofMinutes(50)))).isTrue();
        verify(roomService, times(1)).sendNotice(eq(GUEST), eq(RoomEventType.BREAK_ALERT), anyString());
        assertThat(scheduler.runNext(START.plus(Duration.ofHours(5)))).isFalse();
    }

    @Test
    void 알림이_꺼져_있으면_예약하지_않는다() {
        when(guestService.getSetting(GUEST)).thenReturn(new SettingResponse(false, 50));

        breakAlert.onMyStateChanged(focusStarted());

        assertThat(scheduler.pendingCount()).isZero();
    }

    @Test
    void 알림_전에_집중이_끝나면_취소한다() {
        when(guestService.getSetting(GUEST)).thenReturn(new SettingResponse(true, 50));
        breakAlert.onMyStateChanged(focusStarted());

        breakAlert.onMyStateChanged(new MyStateChangedEvent(GUEST,
                new MyState(SeatState.BREAK, START.plus(Duration.ofMinutes(30))), null));

        assertThat(scheduler.pendingCount()).isZero();
        verify(roomService, never()).sendNotice(eq(GUEST), eq(RoomEventType.BREAK_ALERT), anyString());
    }

    @Test
    void 예약된_세션이_더_이상_진행_중이_아니면_알리지_않는다() {
        when(guestService.getSetting(GUEST)).thenReturn(new SettingResponse(true, 50));
        when(focusService.findRunningSessionId(GUEST)).thenReturn(Optional.of(99L));
        breakAlert.onMyStateChanged(focusStarted());

        scheduler.runNext(START.plus(Duration.ofMinutes(50)));

        verify(roomService, never()).sendNotice(eq(GUEST), eq(RoomEventType.BREAK_ALERT), anyString());
    }

    private static MyStateChangedEvent focusStarted() {
        return new MyStateChangedEvent(GUEST, new MyState(SeatState.FOCUS, START), SESSION);
    }
}
