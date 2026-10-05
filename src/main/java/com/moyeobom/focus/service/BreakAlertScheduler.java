package com.moyeobom.focus.service;

import com.moyeobom.common.config.SchedulingConfig;
import com.moyeobom.common.message.RefreshMessages;
import com.moyeobom.focus.domain.MyStateChangedEvent;
import com.moyeobom.guest.dto.GuestDtos.SettingResponse;
import com.moyeobom.guest.service.GuestService;
import com.moyeobom.room.domain.SeatState;
import com.moyeobom.room.dto.RoomDtos.RoomEventType;
import com.moyeobom.room.service.RoomService;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 휴식 알림을 켠 게스트가 설정한 시간만큼 한 번에 집중하면 리프레시 문구와 함께 한 번 알린다.
 * 집중을 시작할 때 한 번 예약하고, 집중이 끝나면 취소한다. 설정을 바꾸면 다음 집중부터 적용된다.
 */
@Component
public class BreakAlertScheduler {

    private final GuestService guestService;
    private final FocusService focusService;
    private final RoomService roomService;
    private final RefreshMessages refreshMessages;
    private final TaskScheduler taskScheduler;
    private final Map<Long, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    public BreakAlertScheduler(GuestService guestService, FocusService focusService, RoomService roomService,
                               RefreshMessages refreshMessages,
                               @Qualifier(SchedulingConfig.TASK_SCHEDULER) TaskScheduler taskScheduler) {
        this.guestService = guestService;
        this.focusService = focusService;
        this.roomService = roomService;
        this.refreshMessages = refreshMessages;
        this.taskScheduler = taskScheduler;
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onMyStateChanged(MyStateChangedEvent event) {
        Long guestId = event.guestId();
        cancel(guestId);
        if (event.myState().state() != SeatState.FOCUS || event.sessionId() == null) {
            return;
        }
        SettingResponse setting = guestService.getSetting(guestId);
        if (!setting.breakAlertEnabled()) {
            return;
        }
        Instant alertAt = event.myState().since().plus(Duration.ofMinutes(setting.breakAlertMinutes()));
        pending.put(guestId, taskScheduler.schedule(() -> alert(guestId, event.sessionId()), alertAt));
    }

    void alert(Long guestId, Long sessionId) {
        pending.remove(guestId);
        // 그 사이 세션이 바뀌었거나 끝났으면 알리지 않는다
        boolean stillFocusing = focusService.findRunningSessionId(guestId)
                .map(sessionId::equals)
                .orElse(false);
        if (stillFocusing) {
            roomService.sendNotice(guestId, RoomEventType.BREAK_ALERT, refreshMessages.pick());
        }
    }

    private void cancel(Long guestId) {
        ScheduledFuture<?> previous = pending.remove(guestId);
        if (previous != null) {
            previous.cancel(false);
        }
    }
}
