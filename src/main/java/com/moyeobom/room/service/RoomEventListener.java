package com.moyeobom.room.service;

import com.moyeobom.focus.domain.MyStateChangedEvent;
import com.moyeobom.task.domain.TaskCountsChangedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 기록이 커밋된 뒤에 내 자리에 반영한다. 롤백된 변경은 열람실에 보이지 않는다.
 */
@Component
@RequiredArgsConstructor
public class RoomEventListener {

    private final RoomService roomService;

    @TransactionalEventListener(fallbackExecution = true)
    public void onMyStateChanged(MyStateChangedEvent event) {
        roomService.changeMyState(event.guestId(), event.myState());
    }

    @TransactionalEventListener(fallbackExecution = true)
    public void onCountsChanged(TaskCountsChangedEvent event) {
        roomService.changeMyCounts(event.guestId(), event.counts());
    }
}
