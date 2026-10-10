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
import com.moyeobom.room.domain.RoomCodes;
import com.moyeobom.room.domain.Seat;
import com.moyeobom.room.dto.RoomDtos.CountsPayload;
import com.moyeobom.room.dto.RoomDtos.NoticePayload;
import com.moyeobom.room.dto.RoomDtos.OccupantView;
import com.moyeobom.room.dto.RoomDtos.RoomEnterResponse;
import com.moyeobom.room.dto.RoomDtos.RoomEvent;
import com.moyeobom.room.dto.RoomDtos.RoomEventType;
import com.moyeobom.room.dto.RoomDtos.RoomLookupResponse;
import com.moyeobom.room.dto.RoomDtos.SeatLeftPayload;
import com.moyeobom.room.dto.RoomDtos.SeatView;
import com.moyeobom.room.dto.RoomDtos.SnapshotPayload;
import com.moyeobom.room.dto.RoomDtos.StatePayload;
import com.moyeobom.room.websocket.ConnectionRegistry;
import com.moyeobom.task.domain.TaskCounts;
import com.moyeobom.task.service.TaskQueryService;
import java.time.Clock;
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
 * 열람실 생명주기.
 * <ul>
 *   <li>만들기: 가상 자리 n, 실제 자리 n으로 만들고 만든 사람이 실제 자리에 앉는다. 입장 코드가 생긴다.</li>
 *   <li>들어가기: 코드를 아는 사람은 누구나 비어 있는 실제 자리에 앉는다.</li>
 *   <li>연결이 끊기면 그 사람의 자리를 정해진 시간 동안 맡아 두고, 돌아오지 않으면 자리를 비운다.</li>
 *   <li>실제 참여자가 모두 나가면 방을 정리하고 코드도 무효가 된다.</li>
 * </ul>
 * 만들기·들어가기·나가기는 이 서비스의 모니터로 한 줄로 세우고, 자리 변경은 방 락 안에서 한다.
 */
@Slf4j
@Service
public class RoomService {

    private final RoomRegistry roomRegistry;
    private final RoomMessenger messenger;
    private final MateScheduler mateScheduler;
    private final FocusService focusService;
    private final TaskQueryService taskQueryService;
    private final ConnectionRegistry connectionRegistry;
    private final RoomProperties properties;
    private final TaskScheduler taskScheduler;
    private final RandomGenerator random;
    private final Clock clock;

    public RoomService(RoomRegistry roomRegistry, RoomMessenger messenger, MateScheduler mateScheduler,
                       FocusService focusService, TaskQueryService taskQueryService,
                       ConnectionRegistry connectionRegistry, RoomProperties properties,
                       @Qualifier(SchedulingConfig.TASK_SCHEDULER) TaskScheduler taskScheduler,
                       RandomGenerator random, Clock clock) {
        this.roomRegistry = roomRegistry;
        this.messenger = messenger;
        this.mateScheduler = mateScheduler;
        this.focusService = focusService;
        this.taskQueryService = taskQueryService;
        this.connectionRegistry = connectionRegistry;
        this.properties = properties;
        this.taskScheduler = taskScheduler;
        this.random = random;
        this.clock = clock;
    }

    /**
     * 새 열람실을 만들고 들어간다. 이미 다른 방에 있으면 그 방에서 먼저 나온다.
     */
    public RoomEnterResponse create(Long guestId, int virtualSeats, int realSeats) {
        int total = virtualSeats + realSeats;
        if (realSeats < 1 || virtualSeats < 0 || total > Room.MAX_SEAT_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "실제 자리는 1명 이상, 합계는 9명까지 고를 수 있어요.");
        }
        Occupant me = newMember(guestId);
        Room room;
        synchronized (this) {
            leaveCurrent(guestId);
            room = Room.create(newRoomId(), newCode(), virtualSeats, realSeats, random);
            room.withLock(() -> {
                room.addMember(guestId, pickWaitingSeat(room), me);
                mateScheduler.populate(room, clock.instant());
                holdIfDisconnected(room, guestId);
            });
            roomRegistry.register(room);
            roomRegistry.joined(guestId, room);
        }
        log.debug("열람실 생성 roomId={}, code={}, 가상 {}, 실제 {}", room.getRoomId(), room.getCode(), virtualSeats, realSeats);
        return responseOf(room, guestId);
    }

    /**
     * 코드로 열람실에 들어간다. 이미 그 방에 있으면 그대로 돌려주고, 다른 방에 있으면 먼저 나온다.
     */
    public RoomEnterResponse join(Long guestId, String rawCode) {
        String code = RoomCodes.normalize(rawCode);
        Room room = roomRegistry.findByCode(code).orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));
        if (room.hasMember(guestId)) {
            return responseOf(room, guestId);
        }
        Occupant me = newMember(guestId);
        synchronized (this) {
            room.withLock(() -> {
                if (room.isClosed()) {
                    throw new BusinessException(ErrorCode.ROOM_NOT_FOUND);
                }
                if (room.waitingSeats().isEmpty()) {
                    throw new BusinessException(ErrorCode.ROOM_FULL);
                }
            });
            leaveCurrent(guestId);
            room.withLock(() -> {
                // 다른 방에서 나오는 사이 자리가 찼을 수 있어 다시 확인한다
                if (room.isClosed()) {
                    throw new BusinessException(ErrorCode.ROOM_NOT_FOUND);
                }
                if (room.waitingSeats().isEmpty()) {
                    throw new BusinessException(ErrorCode.ROOM_FULL);
                }
                Seat seat = pickWaitingSeat(room);
                room.addMember(guestId, seat, me);
                messenger.toRoom(room, RoomEventType.SEAT_JOINED, seat.getSeatNo(), OccupantView.of(me));
                holdIfDisconnected(room, guestId);
            });
            roomRegistry.joined(guestId, room);
        }
        return responseOf(room, guestId);
    }

    /** 코드로 들어가기 전에 방이 있는지, 자리가 남았는지 확인한다 */
    public RoomLookupResponse lookup(String rawCode) {
        Room room = roomRegistry.findByCode(RoomCodes.normalize(rawCode))
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));
        return room.withLock(() -> new RoomLookupResponse(room.getCode(), room.getSeatCount(), room.getRealSeatCount(),
                room.waitingSeats().size()));
    }

    /** 내가 있는 열람실. 없으면 404 */
    public RoomEnterResponse current(Long guestId) {
        Room room = roomRegistry.findByGuestId(guestId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));
        return responseOf(room, guestId);
    }

    /** 열람실에서 나온다. 오늘 마무리를 확정할 때도 부른다. 방에 아무도 없으면 방을 정리한다 */
    public synchronized void leave(Long guestId) {
        leaveCurrent(guestId);
    }

    public Optional<String> findRoomId(Long guestId) {
        return roomRegistry.findByGuestId(guestId).map(Room::getRoomId);
    }

    public void sendSnapshot(Long guestId) {
        RoomEvent event = roomRegistry.findByGuestId(guestId)
                .map(room -> room.withLock(() -> snapshotOf(room, guestId)))
                .orElseGet(() -> messenger.event(RoomEventType.ROOM_NOT_FOUND, null, null, null));
        messenger.toUser(guestId, RoomMessenger.SNAPSHOT_QUEUE, event);
    }

    public void changeMyState(Long guestId, MyState myState) {
        roomRegistry.findByGuestId(guestId).ifPresent(room -> room.withLock(() -> {
            if (room.isClosed()) {
                return;
            }
            room.seatOf(guestId).ifPresent(seat -> seat.occupant().ifPresent(me -> {
                me.changeState(myState.state(), myState.since());
                messenger.toRoom(room, RoomEventType.STATE_CHANGED, seat.getSeatNo(),
                        new StatePayload(me.getState(), me.getSince()));
            }));
        }));
    }

    public void changeMyCounts(Long guestId, TaskCounts counts) {
        roomRegistry.findByGuestId(guestId).ifPresent(room -> room.withLock(() -> {
            if (room.isClosed()) {
                return;
            }
            room.seatOf(guestId).ifPresent(seat -> seat.occupant().ifPresent(me -> {
                me.changeCounts(counts.completedCount(), counts.remainingCount());
                messenger.toRoom(room, RoomEventType.COUNTS_CHANGED, seat.getSeatNo(),
                        new CountsPayload(me.getCompletedCount(), me.getRemainingCount()));
            }));
        }));
    }

    /** 나에게만 보내는 알림(휴식 알림 등) */
    public void sendNotice(Long guestId, RoomEventType type, String message) {
        Optional<Room> room = roomRegistry.findByGuestId(guestId);
        String roomId = room.map(Room::getRoomId).orElse(null);
        Integer seatNo = room.flatMap(r -> r.seatOf(guestId)).map(Seat::getSeatNo).orElse(null);
        messenger.toUser(guestId, RoomMessenger.NOTICE_QUEUE,
                messenger.event(type, roomId, seatNo, new NoticePayload(message)));
    }

    /** 다시 연결되면 자리 비우기 예약을 취소한다 */
    public void onConnected(Long guestId) {
        roomRegistry.findByGuestId(guestId).ifPresent(room -> room.withLock(() -> room.cancelGrace(guestId)));
    }

    /** 연결이 끊기면 자리를 정해진 시간 동안 맡아 둔다 */
    public void onDisconnected(Long guestId) {
        roomRegistry.findByGuestId(guestId).ifPresent(room -> room.withLock(() -> {
            if (!room.isClosed()) {
                room.startGrace(guestId, scheduleRelease(room, guestId));
            }
        }));
    }

    private void holdIfDisconnected(Room room, Long guestId) {
        // 입장 직후 WebSocket이 연결되지 않으면 맡아 둔 시간이 지난 뒤 자리를 비운다.
        // 이미 연결된 채로 방을 옮긴 경우에는 연결 이벤트가 다시 오지 않으므로 예약하지 않는다.
        if (!connectionRegistry.isConnected(guestId)) {
            room.startGrace(guestId, scheduleRelease(room, guestId));
        }
    }

    private ScheduledFuture<?> scheduleRelease(Room room, Long guestId) {
        return taskScheduler.schedule(() -> release(room, guestId), clock.instant().plus(properties.gracePeriod()));
    }

    private synchronized void release(Room room, Long guestId) {
        boolean stillAway = room.withLock(() -> !room.isClosed() && room.isInGrace(guestId));
        if (stillAway) {
            log.debug("맡아 둔 시간이 지나 자리를 비웁니다. roomId={}, guestId={}", room.getRoomId(), guestId);
            leaveRoom(room, guestId);
        }
    }

    private void leaveCurrent(Long guestId) {
        roomRegistry.findByGuestId(guestId).ifPresent(room -> leaveRoom(room, guestId));
    }

    private void leaveRoom(Room room, Long guestId) {
        boolean empty = room.withLock(() -> {
            room.removeMember(guestId).ifPresent(seat ->
                    messenger.toRoom(room, RoomEventType.SEAT_LEFT, seat.getSeatNo(), new SeatLeftPayload(true)));
            if (room.members().isEmpty()) {
                room.close();
                return true;
            }
            return false;
        });
        roomRegistry.left(guestId, room);
        if (empty) {
            roomRegistry.remove(room);
            log.debug("참여자가 모두 나가 열람실을 정리합니다. roomId={}", room.getRoomId());
        }
    }

    private Occupant newMember(Long guestId) {
        TaskCounts counts = taskQueryService.countsOf(guestId);
        if (counts.total() == 0) {
            throw new BusinessException(ErrorCode.NO_TASK_FOR_ROOM);
        }
        MyState myState = focusService.getMyState(guestId);
        return new Occupant(Nicknames.random(random), CharacterParts.random(random), OccupantKind.REAL,
                myState.state(), myState.since(), counts.completedCount(), counts.remainingCount());
    }

    private Seat pickWaitingSeat(Room room) {
        List<Seat> waiting = room.waitingSeats();
        return waiting.get(random.nextInt(waiting.size()));
    }

    private RoomEnterResponse responseOf(Room room, Long guestId) {
        return room.withLock(() -> {
            Seat seat = room.seatOf(guestId).orElseThrow(() -> new BusinessException(ErrorCode.ROOM_NOT_FOUND));
            return new RoomEnterResponse(room.getRoomId(), room.getCode(), room.getSeatCount(), room.getRealSeatCount(),
                    seat.getSeatNo(), seat.occupant().map(Occupant::getNickname).orElseThrow());
        });
    }

    private RoomEvent snapshotOf(Room room, Long guestId) {
        List<SeatView> seats = room.seats().stream()
                .map(seat -> new SeatView(seat.getSeatNo(), seat.occupant().map(OccupantView::of).orElse(null),
                        seat.isWaiting()))
                .toList();
        int mySeatNo = room.seatOf(guestId).map(Seat::getSeatNo).orElse(0);
        return messenger.event(RoomEventType.ROOM_SNAPSHOT, room.getRoomId(), null,
                new SnapshotPayload(clock.instant(), room.getCode(), room.getSeatCount(), mySeatNo, seats));
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

    private String newCode() {
        String code;
        do {
            code = RoomCodes.random(random);
        } while (roomRegistry.containsCode(code));
        return code;
    }
}
