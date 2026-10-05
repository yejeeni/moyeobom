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
    void 할_일이_없으면_입장할_수_없고_같은_방에_다시_들어오면_같은_자리다() throws Exception {
        mockMvc.perform(post("/api/v1/rooms/enter").header("X-Guest-Id", guest))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("NO_TASK_FOR_ROOM"));

        startSprint();
        Map<String, Object> first = enter();
        Map<String, Object> second = enter();

        assertThat(second).isEqualTo(first);
    }

    @Test
    void 구독하면_스냅샷을_받고_내_상태_변화가_내_자리에_전달된다() throws Exception {
        long taskId = startSprint();
        Map<String, Object> entered = enter();
        String roomId = (String) entered.get("roomId");
        int mySeat = (Integer) entered.get("seatNo");

        StompSession session = connect(guest);
        BlockingQueue<Map<String, Object>> topic = subscribe(session, "/topic/rooms/" + roomId);
        BlockingQueue<Map<String, Object>> snapshots = subscribe(session, "/user/queue/room-snapshot");

        Map<String, Object> snapshot = poll(snapshots, event -> true);
        assertThat(snapshot.get("type")).isEqualTo("ROOM_SNAPSHOT");
        assertThat((Integer) JsonPath.read(snapshot, "$.payload.mySeatNo")).isEqualTo(mySeat);
        List<Object> occupants = JsonPath.read(snapshot, "$.payload.seats[*].occupant");
        assertThat(occupants).hasSize(9).doesNotContainNull();
        assertThat((String) JsonPath.read(snapshot, "$.payload.seats[" + (mySeat - 1) + "].occupant.nickname"))
                .isEqualTo(entered.get("nickname"));
        assertThat((String) JsonPath.read(snapshot, "$.payload.seats[" + (mySeat - 1) + "].occupant.state"))
                .isEqualTo("IDLE");
        assertThat(snapshot.toString()).doesNotContain("VIRTUAL", "REAL");

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/focus").header("X-Guest-Id", guest))
                .andExpect(status().isOk());

        Map<String, Object> changed = poll(topic,
                event -> "STATE_CHANGED".equals(event.get("type")) && Integer.valueOf(mySeat).equals(event.get("seatNo")));
        assertThat((String) JsonPath.read(changed, "$.payload.state")).isEqualTo("FOCUS");
        assertThat((String) JsonPath.read(changed, "$.payload.since")).isEqualTo("2026-10-05T00:00:00Z");

        mockMvc.perform(post("/api/v1/sprints/current/tasks").header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"추가\"}"))
                .andExpect(status().isCreated());

        Map<String, Object> counts = poll(topic, event -> "COUNTS_CHANGED".equals(event.get("type")));
        assertThat((Integer) JsonPath.read(counts, "$.payload.remainingCount")).isEqualTo(2);
    }

    @Test
    void 연결이_끊기면_집중_세션이_닫히고_다시_연결하면_같은_방_스냅샷을_받는다() throws Exception {
        long taskId = startSprint();
        String roomId = (String) enter().get("roomId");
        StompSession session = connect(guest);
        subscribe(session, "/topic/rooms/" + roomId);
        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/focus").header("X-Guest-Id", guest));
        clock.advance(Duration.ofMinutes(3));

        session.disconnect();
        waitUntil(() -> stateOf(guest).equals("IDLE"));

        StompSession again = connect(guest);
        BlockingQueue<Map<String, Object>> snapshots = subscribe(again, "/user/queue/room-snapshot");
        Map<String, Object> snapshot = poll(snapshots, event -> true);
        assertThat(snapshot.get("roomId")).isEqualTo(roomId);
    }

    @Test
    void 모르는_게스트는_연결할_수_없다() {
        assertThatThrownBy(() -> connect("3f1c2a8e-0000-4000-8000-000000000000"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void 방이_없으면_ROOM_NOT_FOUND를_받는다() throws Exception {
        StompSession session = connect(guest);
        BlockingQueue<Map<String, Object>> snapshots = subscribe(session, "/user/queue/room-snapshot");

        assertThat(poll(snapshots, event -> true).get("type")).isEqualTo("ROOM_NOT_FOUND");
    }

    private long startSprint() throws Exception {
        String body = mockMvc.perform(post("/api/v1/sprints").header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"tasks\": [{\"title\": \"알고리즘\"}]}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.sprint.tasks[0].taskId")).longValue();
    }

    private Map<String, Object> enter() throws Exception {
        String body = mockMvc.perform(post("/api/v1/rooms/enter").header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$");
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
}
