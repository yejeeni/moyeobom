package com.moyeobom.room.service;

import com.moyeobom.common.config.SchedulingConfig;
import com.moyeobom.common.exception.BusinessException;
import com.moyeobom.common.exception.ErrorCode;
import com.moyeobom.focus.domain.MyState;
import com.moyeobom.focus.service.FocusService;
import com.moyeobom.mate.service.MateScheduler;
import com.moyeobom.room.config.RoomProperties;
import com.moyeobom.room.domain.CharacterParts;
import com.moyeobom.room.domain.Nicknames;
import com.moyeobom.room.domain.Occupant;
import com.moyeobom.room.domain.OccupantKind;
import com.moyeobom.room.domain.Room;
import com.moyeobom.room.domain.RoomStatus;
import com.moyeobom.room.dto.RoomDtos.CountsPayload;
import com.moyeobom.room.dto.RoomDtos.NoticePayload;
import com.moyeobom.room.dto.RoomDtos.OccupantView;
import com.moyeobom.room.dto.RoomDtos.RoomEnterResponse;
import com.moyeobom.room.dto.RoomDtos.RoomEvent;
import com.moyeobom.room.dto.RoomDtos.RoomEventType;
import com.moyeobom.room.dto.RoomDtos.SeatView;
import com.moyeobom.room.dto.RoomDtos.SnapshotPayload;
import com.moyeobom.room.dto.RoomDtos.StatePayload;
import com.moyeobom.task.domain.TaskCounts;
import com.moyeobom.task.service.TaskQueryService;
import java.time.Clock;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ScheduledFuture;
import java.util.random.RandomGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

/**
 * 열람실 생명주기: 입장으로 생성 → WebSocket 연결 동안 활성 → 끊기면 유예 → 유예 만료나 마무리로 정리.
 */
@Slf4j
@Service
public class RoomService {

    private final RoomRegistry roomRegistry;
    private final RoomMessenger messenger;
    private final MateScheduler mateScheduler;
    private final FocusService focusService;
    private final TaskQueryService taskQueryService;
    private final RoomProperties properties;
    private final TaskScheduler taskScheduler;
    private final RandomGenerator random;
    private final Clock clock;

    public RoomService(RoomRegistry roomRegistry, RoomMessenger messenger, MateScheduler mateScheduler,
                       FocusService focusService, TaskQueryService taskQueryService, RoomProperties properties,
                       @Qualifier(SchedulingConfig.TASK_SCHEDULER) TaskScheduler taskScheduler,
                       RandomGenerator random, Clock clock) {
        this.roomRegistry = roomRegistry;
        this.messenger = messenger;
        this.mateScheduler = mateScheduler;
        this.focusService = focusService;
        this.taskQueryService = taskQueryService;
        this.properties = properties;
        this.taskScheduler = taskScheduler;
        this.random = random;
        this.clock = clock;
    }

    /**
     * 열람실에 입장한다. 열린 스프린트에 할 일이 있어야 한다.
     * 이미 방이 있으면 그 방을 돌려주고, 인원이 다르면 그 방을 정리하고 새 인원으로 만든다.
     *
     * @param seatCount 인원(나 포함 1~9). null이면 9명
     */
    public RoomEnterResponse enter(Long guestId, Integer seatCount) {
        int size = seatCount == null ? Room.MAX_SEAT_COUNT : seatCount;
        if (size < 1 || size > Room.MAX_SEAT_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "인원은 1~9명 중에서 골라 주세요.");
        }
        TaskCounts counts = taskQueryService.countsOf(guestId);
        if (counts.total() == 0) {
            throw new BusinessException(ErrorCode.NO_TASK_FOR_ROOM);
        }
        MyState myState = focusService.getMyState(guestId);
        Room room = findOrCreate(guestId, size, myState, counts);
        return room.withLock(() -> new RoomEnterResponse(room.getRoomId(), room.getSeatCount(), room.getMySeatNo(),
                room.mySeat().occupant().map(Occupant::getNickname).orElseThrow()));
    }

    public Optional<String> findRoomId(Long guestId) {
        return roomRegistry.findByGuestId(guestId).map(Room::getRoomId);
    }

    public void sendSnapshot(Long guestId) {
        RoomEvent event = roomRegistry.findByGuestId(guestId)
                .map(room -> room.withLock(() -> snapshotOf(room)))
                .orElseGet(() -> messenger.event(RoomEventType.ROOM_NOT_FOUND, null, null, null));
        messenger.toUser(guestId, RoomMessenger.SNAPSHOT_QUEUE, event);
    }

    public void changeMyState(Long guestId, MyState myState) {
        roomRegistry.findByGuestId(guestId).ifPresent(room -> room.withLock(() -> {
            if (room.isClosed()) {
                return;
            }
            room.mySeat().occupant().ifPresent(me -> {
                me.changeState(myState.state(), myState.since());
                messenger.toRoom(room, RoomEventType.STATE_CHANGED, room.getMySeatNo(),
                        new StatePayload(me.getState(), me.getSince()));
            });
        }));
    }

    public void changeMyCounts(Long guestId, TaskCounts counts) {
        roomRegistry.findByGuestId(guestId).ifPresent(room -> room.withLock(() -> {
            if (room.isClosed()) {
                return;
            }
            room.mySeat().occupant().ifPresent(me -> {
                me.changeCounts(counts.completedCount(), counts.remainingCount());
                messenger.toRoom(room, RoomEventType.COUNTS_CHANGED, room.getMySeatNo(),
                        new CountsPayload(me.getCompletedCount(), me.getRemainingCount()));
            });
        }));
    }

    /** 나에게만 보내는 알림(휴식 알림 등) */
    public void sendNotice(Long guestId, RoomEventType type, String message) {
        String roomId = findRoomId(guestId).orElse(null);
        Integer seatNo = roomRegistry.findByGuestId(guestId).map(Room::getMySeatNo).orElse(null);
        messenger.toUser(guestId, RoomMessenger.NOTICE_QUEUE,
                messenger.event(type, roomId, seatNo, new NoticePayload(message)));
    }

    public void onConnected(Long guestId) {
        roomRegistry.findByGuestId(guestId).ifPresent(room -> room.withLock(room::activate));
    }

    public void onDisconnected(Long guestId) {
        roomRegistry.findByGuestId(guestId).ifPresent(this::startGrace);
    }

    /** 오늘 마무리를 확정하면 방을 지운다. 다음 입장 때는 새 방과 새 닉네임을 받는다. */
    public void closeRoomOf(Long guestId) {
        roomRegistry.findByGuestId(guestId).ifPresent(this::close);
    }

    private synchronized Room findOrCreate(Long guestId, int seatCount, MyState myState, TaskCounts counts) {
        Optional<Room> existing = roomRegistry.findByGuestId(guestId);
        if (existing.isPresent()) {
            if (existing.get().getSeatCount() == seatCount) {
                return existing.get();
            }
            // 인원을 바꾸면 기존 방을 정리하고 새로 만든다(자리와 닉네임도 새로 받는다)
            close(existing.get());
        }
        Instant now = clock.instant();
        Room room = new Room(newRoomId(), guestId, seatCount, 1 + random.nextInt(seatCount));
        room.withLock(() -> {
            room.mySeat().sit(new Occupant(Nicknames.random(random), CharacterParts.random(random),
                    OccupantKind.REAL, myState.state(), myState.since(), counts.completedCount(),
                    counts.remainingCount()));
            mateScheduler.populate(room, now);
            // 입장 직후 WebSocket이 연결되지 않으면 유예 시간 뒤에 정리한다
            room.startGrace(scheduleExpiry(room));
        });
        roomRegistry.register(room);
        log.debug("열람실 생성 roomId={}, guestId={}, seatCount={}", room.getRoomId(), guestId, seatCount);
        return room;
    }

    private RoomEvent snapshotOf(Room room) {
        List<SeatView> seats = room.seats().stream()
                .map(seat -> new SeatView(seat.getSeatNo(), seat.occupant().map(OccupantView::of).orElse(null)))
                .toList();
        return messenger.event(RoomEventType.ROOM_SNAPSHOT, room.getRoomId(), null,
                new SnapshotPayload(clock.instant(), room.getSeatCount(), room.getMySeatNo(), seats));
    }

    private void startGrace(Room room) {
        room.withLock(() -> {
            if (!room.isClosed()) {
                room.startGrace(scheduleExpiry(room));
            }
        });
    }

    private ScheduledFuture<?> scheduleExpiry(Room room) {
        return taskScheduler.schedule(() -> expire(room), clock.instant().plus(properties.gracePeriod()));
    }

    private void expire(Room room) {
        boolean expired = room.withLock(() -> !room.isClosed() && room.getStatus() == RoomStatus.GRACE);
        if (expired) {
            log.debug("유예 시간이 지나 열람실을 정리합니다. roomId={}", room.getRoomId());
            close(room);
        }
    }

    private void close(Room room) {
        room.withLock(room::close);
        roomRegistry.remove(room);
    }

    private String newRoomId() {
        String roomId;
        do {
            byte[] bytes = new byte[4];
            random.nextBytes(bytes);
            roomId = "r-" + HexFormat.of().formatHex(bytes);
        } while (roomRegistry.containsRoomId(roomId));
        return roomId;
    }
}
