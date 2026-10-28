package com.moyeobom.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.moyeobom.support.IntegrationTestSupport;
import java.lang.reflect.Type;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoomWebSocketTest extends IntegrationTestSupport {

    private static final long TIMEOUT_SECONDS = 5;

    @LocalServerPort
    int port;

    WebSocketStompClient stompClient;
    String guest;

    @BeforeEach
    void setUp() throws Exception {
        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new JacksonJsonMessageConverter());
        guest = issueGuest();
    }

    @AfterEach
    void tearDown() {
        stompClient.stop();
    }

    @Test
    void 할_일이_없으면_열람실을_만들_수_없다() throws Exception {
        create(guest, 8, 1)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_TASK_FOR_ROOM"));
    }

    @Test
    void 가상_자리와_실제_자리를_정해_만들면_코드를_받고_내_자리에_앉는다() throws Exception {
        startSprint(guest);
        Map<String, Object> room = created(guest, 5, 3);

        assertThat(room.get("seatCount")).isEqualTo(8);
        assertThat(room.get("realSeatCount")).isEqualTo(3);
        assertThat((String) room.get("code")).matches("[A-HJKMNP-Z2-9]{6}");
        assertThat(current(guest).get("roomId")).isEqualTo(room.get("roomId"));

        StompSession session = connect(guest);
        Map<String, Object> snapshot = poll(subscribe(session, "/user/queue/room-snapshot"), e -> true);
        assertThat((String) JsonPath.read(snapshot, "$.payload.code")).isEqualTo(room.get("code"));
        assertThat((Integer) JsonPath.read(snapshot, "$.payload.mySeatNo")).isEqualTo(room.get("seatNo"));
        // 나를 뺀 실제 자리 2개는 '초대 대기'로 비어 있다
        List<Boolean> waiting = JsonPath.read(snapshot, "$.payload.seats[*].waiting");
        assertThat(waiting.stream().filter(Boolean::booleanValue).count()).isEqualTo(2);
        List<Object> occupants = JsonPath.read(snapshot, "$.payload.seats[*].occupant");
        assertThat(occupants.stream().filter(Objects::nonNull).count()).isBetween(2L, 6L);
        assertThat(snapshot.toString()).doesNotContain("VIRTUAL", "REAL");
    }

    @Test
    void 합계가_9명을_넘거나_실제_자리가_없으면_만들_수_없다() throws Exception {
        startSprint(guest);
        create(guest, 8, 2).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_INPUT"));
        create(guest, 3, 0).andExpect(status().isBadRequest());
    }

    @Test
    void 코드로_들어온_사람이_실제_자리에_앉고_서로의_상태를_본다() throws Exception {
        startSprint(guest);
        Map<String, Object> room = created(guest, 2, 2);
        String code = (String) room.get("code");
        StompSession hostSession = connect(guest);
        BlockingQueue<Map<String, Object>> hostTopic = subscribe(hostSession, "/topic/rooms/" + room.get("roomId"));

        String friend = issueGuest();
        long friendTask = startSprint(friend);
        mockMvc.perform(get("/api/v1/rooms/lookup").header("X-Guest-Id", friend).param("code", code.toLowerCase()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.waitingSeatCount").value(1));
        Map<String, Object> joined = join(friend, code.toLowerCase().replaceAll("(...)", "$1-"))
                .andExpect(status().isOk()).andReturnMap();
        assertThat(joined.get("roomId")).isEqualTo(room.get("roomId"));

        Map<String, Object> seatJoined = poll(hostTopic, e -> "SEAT_JOINED".equals(e.get("type")));
        assertThat(seatJoined.get("seatNo")).isEqualTo(joined.get("seatNo"));
        assertThat((String) JsonPath.read(seatJoined, "$.payload.nickname")).isEqualTo(joined.get("nickname"));

        StompSession friendSession = connect(friend);
        subscribe(friendSession, "/topic/rooms/" + room.get("roomId"));
        mockMvc.perform(post("/api/v1/tasks/" + friendTask + "/focus").header("X-Guest-Id", friend))
                .andExpect(status().isOk());
        Map<String, Object> changed = poll(hostTopic,
                e -> "STATE_CHANGED".equals(e.get("type")) && joined.get("seatNo").equals(e.get("seatNo")));
        assertThat((String) JsonPath.read(changed, "$.payload.state")).isEqualTo("FOCUS");

        // 실제 자리가 다 찼다
        String third = issueGuest();
        startSprint(third);
        join(third, code).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("ROOM_FULL"));
    }

    @Test
    void 만들_때_메이트_설정을_고르면_처음부터_적용된다() throws Exception {
        startSprint(guest);
        Map<String, Object> room = new Result(mockMvc.perform(post("/api/v1/rooms").header("X-Guest-Id", guest)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"virtualSeats\": 8, \"realSeats\": 1, \"matesAlwaysFocus\": true, \"matesStay\": true}")))
                .andExpect(status().isCreated()).andReturnMap();
        assertThat(room.get("matesAlwaysFocus")).isEqualTo(true);
        assertThat(room.get("matesStay")).isEqualTo(true);
        assertThat(room.get("owner")).isEqualTo(true);

        Map<String, Object> snapshot = poll(subscribe(connect(guest), "/user/queue/room-snapshot"), e -> true);
        assertThat((Boolean) JsonPath.read(snapshot, "$.payload.matesAlwaysFocus")).isTrue();
        assertThat((Boolean) JsonPath.read(snapshot, "$.payload.matesStay")).isTrue();
        assertThat((Integer) JsonPath.read(snapshot, "$.payload.ownerSeatNo")).isEqualTo(room.get("seatNo"));
        List<String> states = JsonPath.read(snapshot, "$.payload.seats[?(@.seatNo != %d)].occupant.state"
                .formatted((Integer) room.get("seatNo")));
        assertThat(states).isNotEmpty().allMatch("FOCUS"::equals);
    }

    @Test
    void 같이_쓰는_방에서는_만든_사람만_메이트_설정을_바꾸고_보낸_항목만_바뀌며_모두에게_알린다() throws Exception {
        startSprint(guest);
        Map<String, Object> room = created(guest, 3, 2);
        assertThat(room.get("matesAlwaysFocus")).isEqualTo(false);
        assertThat(room.get("matesStay")).isEqualTo(false);
        String friend = issueGuest();
        startSprint(friend);
        Map<String, Object> joined = join(friend, (String) room.get("code")).andReturnMap();
        assertThat(joined.get("owner")).isEqualTo(false);
        BlockingQueue<Map<String, Object>> friendTopic =
                subscribe(connect(friend), "/topic/rooms/" + room.get("roomId"));

        changeSettings(friend, "{\"matesStay\": true}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_ROOM_OWNER"));

        // 보낸 항목만 바뀌고 나머지는 그대로다
        changeSettings(guest, "{\"matesStay\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matesAlwaysFocus").value(false))
                .andExpect(jsonPath("$.matesStay").value(true))
                .andExpect(jsonPath("$.ownerSeatNo").value(room.get("seatNo")));
        Map<String, Object> changed = poll(friendTopic, e -> "ROOM_SETTINGS_CHANGED".equals(e.get("type")));
        assertThat((Boolean) JsonPath.read(changed, "$.payload.matesStay")).isTrue();
        assertThat((Boolean) JsonPath.read(changed, "$.payload.matesAlwaysFocus")).isFalse();

        changeSettings(guest, "{\"matesAlwaysFocus\": true}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matesAlwaysFocus").value(true))
                .andExpect(jsonPath("$.matesStay").value(true));
        assertThat(current(friend)).containsEntry("matesAlwaysFocus", true).containsEntry("matesStay", true);
    }

    @Test
    void 방을_만든_사람이_나가면_남은_사람이_이어받아_바꿀_수_있다() throws Exception {
        startSprint(guest);
        Map<String, Object> room = created(guest, 0, 2);
        String friend = issueGuest();
        startSprint(friend);
        Map<String, Object> joined = join(friend, (String) room.get("code")).andReturnMap();
        BlockingQueue<Map<String, Object>> friendTopic =
                subscribe(connect(friend), "/topic/rooms/" + room.get("roomId"));

        mockMvc.perform(post("/api/v1/rooms/leave").header("X-Guest-Id", guest)).andExpect(status().isNoContent());

        Map<String, Object> handedOver = poll(friendTopic, e -> "ROOM_SETTINGS_CHANGED".equals(e.get("type")));
        assertThat((Integer) JsonPath.read(handedOver, "$.payload.ownerSeatNo")).isEqualTo(joined.get("seatNo"));
        assertThat(current(friend).get("owner")).isEqualTo(true);
        changeSettings(friend, "{\"matesAlwaysFocus\": true}").andExpect(status().isOk());
    }

    @Test
    void 방이_사라진_뒤_같은_코드로_복구하면_같은_코드와_구성과_메이트_설정으로_다시_열린다() throws Exception {
        startSprint(guest);

        Map<String, Object> restored = restore(guest, "RSTAB2", 3, 2, true, false, true)
                .andExpect(status().isOk()).andReturnMap();

        assertThat(restored).containsEntry("code", "RSTAB2").containsEntry("seatCount", 5)
                .containsEntry("realSeatCount", 2).containsEntry("matesAlwaysFocus", true)
                .containsEntry("matesStay", false).containsEntry("owner", true);
        assertThat(current(guest).get("roomId")).isEqualTo(restored.get("roomId"));
    }

    @Test
    void 같이_쓰던_사람은_같은_코드로_복구하면_같은_방에_모이고_원래_주인이_주인을_돌려받는다() throws Exception {
        startSprint(guest);
        String friend = issueGuest();
        startSprint(friend);

        // 친구가 먼저 돌아와 방을 다시 열고, 원래 주인은 나중에 돌아온다
        Map<String, Object> friendRoom = restore(friend, "RSTCD3", 2, 2, false, false, false)
                .andExpect(status().isOk()).andReturnMap();
        assertThat(friendRoom.get("owner")).isEqualTo(true);
        BlockingQueue<Map<String, Object>> friendTopic =
                subscribe(connect(friend), "/topic/rooms/" + friendRoom.get("roomId"));

        Map<String, Object> hostRoom = restore(guest, "RSTCD3", 2, 2, false, false, true)
                .andExpect(status().isOk()).andReturnMap();

        assertThat(hostRoom.get("roomId")).isEqualTo(friendRoom.get("roomId"));
        assertThat(hostRoom.get("owner")).isEqualTo(true);
        assertThat(current(friend).get("owner")).isEqualTo(false);
        Map<String, Object> handedBack = poll(friendTopic, e -> "ROOM_SETTINGS_CHANGED".equals(e.get("type")));
        assertThat((Integer) JsonPath.read(handedBack, "$.payload.ownerSeatNo")).isEqualTo(hostRoom.get("seatNo"));
    }

    @Test
    void 복구로는_지금_열려_있는_보통_방에_들어갈_수_없고_새_코드로_열린다() throws Exception {
        startSprint(guest);
        Map<String, Object> live = created(guest, 2, 2);
        String stranger = issueGuest();
        startSprint(stranger);

        Map<String, Object> restored = restore(stranger, (String) live.get("code"), 2, 2, false, false, true)
                .andExpect(status().isOk()).andReturnMap();

        assertThat(restored.get("roomId")).isNotEqualTo(live.get("roomId"));
        assertThat(restored.get("code")).isNotEqualTo(live.get("code"));
    }

    @Test
    void 서버가_켜진_뒤_복구_가능한_시간이_지나면_410() throws Exception {
        startSprint(guest);
        clock.set(START.plus(Duration.ofMinutes(11)));

        restore(guest, "RSTEF4", 2, 1, false, false, true)
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value("ROOM_RESTORE_EXPIRED"));
    }

    @Test
    void 이미_방에_있으면_복구하지_않고_지금_방을_돌려준다() throws Exception {
        startSprint(guest);
        Map<String, Object> live = created(guest, 2, 1);

        Map<String, Object> restored = restore(guest, "RSTGH5", 3, 1, false, false, true)
                .andExpect(status().isOk()).andReturnMap();

        assertThat(restored.get("roomId")).isEqualTo(live.get("roomId"));
    }

    @Test
    void 방에_없으면_설정을_바꿀_수_없다() throws Exception {
        changeSettings(guest, "{\"matesAlwaysFocus\": true}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
    }

    @Test
    void 없는_코드는_404() throws Exception {
        startSprint(guest);
        join(guest, "ZZZZZZ").andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("ROOM_NOT_FOUND"));
        mockMvc.perform(get("/api/v1/rooms/current").header("X-Guest-Id", guest))
                .andExpect(status().isNotFound());
    }

    @Test
    void 나가면_자리가_다시_초대_대기가_되고_모두_나가면_코드도_사라진다() throws Exception {
        startSprint(guest);
        Map<String, Object> room = created(guest, 0, 2);
        String code = (String) room.get("code");
        StompSession hostSession = connect(guest);
        BlockingQueue<Map<String, Object>> hostTopic = subscribe(hostSession, "/topic/rooms/" + room.get("roomId"));

        String friend = issueGuest();
        startSprint(friend);
        Map<String, Object> joined = join(friend, code).andReturnMap();
        mockMvc.perform(post("/api/v1/rooms/leave").header("X-Guest-Id", friend)).andExpect(status().isNoContent());

        Map<String, Object> left = poll(hostTopic, e -> "SEAT_LEFT".equals(e.get("type")));
        assertThat(left.get("seatNo")).isEqualTo(joined.get("seatNo"));
        mockMvc.perform(get("/api/v1/rooms/lookup").header("X-Guest-Id", guest).param("code", code))
                .andExpect(jsonPath("$.waitingSeatCount").value(1));

        mockMvc.perform(post("/api/v1/rooms/leave").header("X-Guest-Id", guest)).andExpect(status().isNoContent());
        mockMvc.perform(get("/api/v1/rooms/lookup").header("X-Guest-Id", guest).param("code", code))
                .andExpect(status().isNotFound());
    }

    @Test
    void 나간_방의_알림은_구독을_남겨_두어도_받지_않는다() throws Exception {
        startSprint(guest);
        Map<String, Object> room = created(guest, 0, 3);
        String code = (String) room.get("code");
        String topicPath = "/topic/rooms/" + room.get("roomId");
        BlockingQueue<Map<String, Object>> hostTopic = subscribe(connect(guest), topicPath);

        String friend = issueGuest();
        startSprint(friend);
        join(friend, code).andExpect(status().isOk());
        BlockingQueue<Map<String, Object>> friendTopic = subscribe(connect(friend), topicPath);

        // 친구의 구독이 브로커에 등록됐는지 다음 입장 알림으로 확인한다
        String third = issueGuest();
        startSprint(third);
        Map<String, Object> thirdJoined = join(third, code).andReturnMap();
        poll(friendTopic, e -> "SEAT_JOINED".equals(e.get("type")) && thirdJoined.get("seatNo").equals(e.get("seatNo")));

        // 친구는 나가지만 클라이언트가 구독을 끊지 않고 남겨 둔다
        mockMvc.perform(post("/api/v1/rooms/leave").header("X-Guest-Id", friend)).andExpect(status().isNoContent());
        poll(hostTopic, e -> "SEAT_LEFT".equals(e.get("type")));
        friendTopic.clear();

        String fourth = issueGuest();
        startSprint(fourth);
        Map<String, Object> fourthJoined = join(fourth, code).andReturnMap();
        poll(hostTopic, e -> "SEAT_JOINED".equals(e.get("type")) && fourthJoined.get("seatNo").equals(e.get("seatNo")));

        Thread.sleep(300);
        assertThat(friendTopic).isEmpty();
    }

    @Test
    void 다른_방을_만들면_이전_방에서_나온다() throws Exception {
        startSprint(guest);
        Map<String, Object> first = created(guest, 3, 1);
        Map<String, Object> second = created(guest, 1, 1);

        assertThat(second.get("roomId")).isNotEqualTo(first.get("roomId"));
        mockMvc.perform(get("/api/v1/rooms/lookup").header("X-Guest-Id", guest).param("code", (String) first.get("code")))
                .andExpect(status().isNotFound());
    }

    @Test
    void 연결이_끊기면_집중_세션이_닫히고_다시_연결하면_같은_방_스냅샷을_받는다() throws Exception {
        long taskId = startSprint(guest);
        String roomId = (String) created(guest, 8, 1).get("roomId");
        StompSession session = connect(guest);
        subscribe(session, "/topic/rooms/" + roomId);
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/focus").header("X-Guest-Id", guest));
        clock.advance(Duration.ofMinutes(3));

        session.disconnect();
        waitUntil(() -> stateOf(guest).equals("IDLE"));

        StompSession again = connect(guest);
        Map<String, Object> snapshot = poll(subscribe(again, "/user/queue/room-snapshot"), e -> true);
        assertThat(snapshot.get("roomId")).isEqualTo(roomId);
    }

    @Test
    void 다른_사람의_열람실은_구독할_수_없고_모르는_게스트는_연결할_수_없다() throws Exception {
        startSprint(guest);
        String roomId = (String) created(guest, 8, 1).get("roomId");
        String stranger = issueGuest();
        StompSession session = connect(stranger);
        BlockingQueue<Map<String, Object>> topic = subscribe(session, "/topic/rooms/" + roomId);
        waitUntil(() -> !session.isConnected());
        assertThat(topic).isEmpty();

        assertThatThrownBy(() -> connect("3f1c2a8e-0000-4000-8000-000000000000")).isInstanceOf(Exception.class);
    }

    @Test
    void 방이_없으면_ROOM_NOT_FOUND를_받는다() throws Exception {
        StompSession session = connect(guest);
        BlockingQueue<Map<String, Object>> snapshots = subscribe(session, "/user/queue/room-snapshot");

        assertThat(poll(snapshots, event -> true).get("type")).isEqualTo("ROOM_NOT_FOUND");
    }

    private long startSprint(String guestId) throws Exception {
        String body = mockMvc.perform(post("/api/v1/sprints").header("X-Guest-Id", guestId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tasks\": [{\"title\": \"알고리즘\"}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.sprint.tasks[0].taskId")).longValue();
    }

    private Result create(String guestId, int virtualSeats, int realSeats) throws Exception {
        return new Result(mockMvc.perform(post("/api/v1/rooms").header("X-Guest-Id", guestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"virtualSeats\": %d, \"realSeats\": %d}".formatted(virtualSeats, realSeats))));
    }

    private Map<String, Object> created(String guestId, int virtualSeats, int realSeats) throws Exception {
        return create(guestId, virtualSeats, realSeats).andExpect(status().isCreated()).andReturnMap();
    }

    private Result join(String guestId, String code) throws Exception {
        return new Result(mockMvc.perform(post("/api/v1/rooms/join").header("X-Guest-Id", guestId)
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\": \"" + code + "\"}")));
    }

    private Result restore(String guestId, String code, int virtualSeats, int realSeats, boolean alwaysFocus,
                           boolean stay, boolean wasOwner) throws Exception {
        return new Result(mockMvc.perform(post("/api/v1/rooms/restore").header("X-Guest-Id", guestId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"code\": \"%s\", \"virtualSeats\": %d, \"realSeats\": %d, \"matesAlwaysFocus\": %b, "
                        + "\"matesStay\": %b, \"wasOwner\": %b}")
                        .formatted(code, virtualSeats, realSeats, alwaysFocus, stay, wasOwner))));
    }

    private Result changeSettings(String guestId, String body) throws Exception {
        return new Result(mockMvc.perform(patch("/api/v1/rooms/current/settings").header("X-Guest-Id", guestId)
                .contentType(MediaType.APPLICATION_JSON).content(body)));
    }

    private Map<String, Object> current(String guestId) throws Exception {
        return new Result(mockMvc.perform(get("/api/v1/rooms/current").header("X-Guest-Id", guestId)))
                .andExpect(status().isOk()).andReturnMap();
    }

    private String stateOf(String guestId) throws Exception {
        String body = mockMvc.perform(get("/api/v1/focus/current").header("X-Guest-Id", guestId))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.state");
    }

    private StompSession connect(String guestId) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("X-Guest-Id", guestId);
        return stompClient.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() {
                }).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private BlockingQueue<Map<String, Object>> subscribe(StompSession session, String destination) {
        BlockingQueue<Map<String, Object>> queue = new LinkedBlockingQueue<>();
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return Map.class;
            }

            @Override
            @SuppressWarnings("unchecked")
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add((Map<String, Object>) payload);
            }
        });
        return queue;
    }

    private Map<String, Object> poll(BlockingQueue<Map<String, Object>> queue, Predicate<Map<String, Object>> match)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            Map<String, Object> event = queue.poll(100, TimeUnit.MILLISECONDS);
            if (event != null && match.test(event)) {
                return event;
            }
        }
        throw new AssertionError("기다린 이벤트가 오지 않았습니다.");
    }

    private void waitUntil(ThrowingSupplier condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
        while (System.nanoTime() < deadline) {
            if (condition.get()) {
                return;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("조건이 만족되지 않았습니다.");
    }

    @FunctionalInterface
    private interface ThrowingSupplier {
        boolean get() throws Exception;
    }

    /** 응답 검사와 본문 꺼내기를 이어 쓰기 위한 작은 도우미 */
    private record Result(ResultActions actions) {

        Result andExpect(ResultMatcher matcher) throws Exception {
            actions.andExpect(matcher);
            return this;
        }

        Map<String, Object> andReturnMap() throws Exception {
            return JsonPath.read(actions.andReturn().getResponse().getContentAsString(), "$");
        }
    }
}
