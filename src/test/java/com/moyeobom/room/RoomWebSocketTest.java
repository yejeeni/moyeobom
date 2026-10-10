package com.moyeobom.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
