package com.moyeobom.focus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.moyeobom.focus.service.FocusService;
import com.moyeobom.guest.service.GuestService;
import com.moyeobom.support.IntegrationTestSupport;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class FocusApiTest extends IntegrationTestSupport {

    @Autowired
    FocusService focusService;

    @Autowired
    GuestService guestService;

    String guest;
    long firstTask;
    long secondTask;

    @BeforeEach
    void setUp() throws Exception {
        guest = issueGuest();
        String body = mockMvc.perform(post("/api/v1/sprints").header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tasks\": [{\"title\": \"알고리즘\", \"estimatedMinutes\": 60}, {\"title\": \"자소서\"}]}"))
                .andReturn().getResponse().getContentAsString();
        firstTask = ((Number) JsonPath.read(body, "$.sprint.tasks[0].taskId")).longValue();
        secondTask = ((Number) JsonPath.read(body, "$.sprint.tasks[1].taskId")).longValue();
    }

    @Test
    void 집중을_시작하면_서버_시각으로_경과_시간이_올라간다() throws Exception {
        focus(firstTask)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("FOCUS"))
                .andExpect(jsonPath("$.session.taskId").value(firstTask))
                .andExpect(jsonPath("$.session.elapsedSeconds").value(0));

        clock.advance(Duration.ofMinutes(10));

        mockMvc.perform(get("/api/v1/focus/current").header("X-Guest-Id", guest))
                .andExpect(jsonPath("$.state").value("FOCUS"))
                .andExpect(jsonPath("$.since").value("2026-10-05T00:00:00Z"))
                .andExpect(jsonPath("$.session.elapsedSeconds").value(600));
    }

    @Test
    void 다른_할_일을_시작하면_진행_중인_세션이_먼저_기록된다() throws Exception {
        focus(firstTask);
        clock.advance(Duration.ofMinutes(10));
        focus(secondTask).andExpect(jsonPath("$.session.taskId").value(secondTask));

        assertThat(actualSeconds()).containsExactly(600, 0);
    }

    @Test
    void 같은_할_일을_다시_시작하면_진행_중인_세션을_그대로_돌려준다() throws Exception {
        focus(firstTask);
        clock.advance(Duration.ofMinutes(2));

        focus(firstTask).andExpect(jsonPath("$.session.elapsedSeconds").value(120));
    }

    @Test
    void 일분_미만_세션은_저장하지_않는다() throws Exception {
        focus(firstTask);
        clock.advance(Duration.ofSeconds(59));
        stop("STOPPED").andExpect(jsonPath("$.state").value("IDLE"));

        assertThat(actualSeconds()).containsExactly(0, 0);
        mockMvc.perform(delete("/api/v1/tasks/" + firstTask).header("X-Guest-Id", guest))
                .andExpect(status().isNoContent());
    }

    @Test
    void 휴식하면_세션이_기록되고_휴식_상태가_된다() throws Exception {
        focus(firstTask);
        clock.advance(Duration.ofMinutes(25));
        stop("BREAK")
                .andExpect(jsonPath("$.state").value("BREAK"))
                .andExpect(jsonPath("$.since").value("2026-10-05T00:25:00Z"))
                .andExpect(jsonPath("$.session").isEmpty());

        clock.advance(Duration.ofMinutes(5));
        mockMvc.perform(get("/api/v1/focus/current").header("X-Guest-Id", guest))
                .andExpect(jsonPath("$.state").value("BREAK"));

        stop("BREAK")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FOCUS_NOT_RUNNING"));

        focus(secondTask).andExpect(jsonPath("$.state").value("FOCUS"));
        assertThat(actualSeconds()).containsExactly(1500, 0);
    }

    @Test
    void 집중_중인_할_일을_완료하면_세션이_끝나고_대기_상태가_된다() throws Exception {
        focus(firstTask);
        clock.advance(Duration.ofMinutes(30));

        mockMvc.perform(post("/api/v1/tasks/" + firstTask + "/complete").header("X-Guest-Id", guest))
                .andExpect(jsonPath("$.task.actualSeconds").value(1800));

        mockMvc.perform(get("/api/v1/focus/current").header("X-Guest-Id", guest))
                .andExpect(jsonPath("$.state").value("IDLE"))
                .andExpect(jsonPath("$.session").isEmpty());

        focus(firstTask)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_ALREADY_DONE"));
    }

    @Test
    void 기록이_있는_할_일은_삭제할_수_없다() throws Exception {
        focus(firstTask);
        clock.advance(Duration.ofMinutes(5));
        stop("STOPPED");

        mockMvc.perform(delete("/api/v1/tasks/" + firstTask).header("X-Guest-Id", guest))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_HAS_RECORDS"));
    }

    @Test
    void 연결이_끊기면_마지막_신호_시각으로_세션을_닫는다() throws Exception {
        focus(firstTask);
        clock.advance(Duration.ofMinutes(5));
        Long guestId = guestService.authenticate(guest);

        focusService.endForDisconnect(guestId, START.plus(Duration.ofMinutes(4)));

        assertThat(actualSeconds()).containsExactly(240, 0);
        mockMvc.perform(get("/api/v1/focus/current").header("X-Guest-Id", guest))
                .andExpect(jsonPath("$.state").value("IDLE"));
    }

    @Test
    void 확인_신호가_오래된_세션은_마지막_신호_시각으로_닫는다() throws Exception {
        focus(firstTask);
        clock.advance(Duration.ofMinutes(2));
        focusService.touchHeartbeat(List.of(guestService.authenticate(guest)));
        clock.advance(Duration.ofMinutes(10));

        int closed = focusService.endStaleSessions(LocalDateTime.of(2026, 10, 5, 0, 9));

        assertThat(closed).isEqualTo(1);
        assertThat(actualSeconds()).containsExactly(120, 0);
    }

    private ResultActions focus(long taskId) throws Exception {
        return mockMvc.perform(post("/api/v1/tasks/" + taskId + "/focus").header("X-Guest-Id", guest));
    }

    private ResultActions stop(String reason) throws Exception {
        return mockMvc.perform(post("/api/v1/focus/stop").header("X-Guest-Id", guest)
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\": \"" + reason + "\"}"));
    }

    private List<Integer> actualSeconds() throws Exception {
        String body = mockMvc.perform(get("/api/v1/sprints/current").header("X-Guest-Id", guest))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.sprint.tasks[*].actualSeconds");
    }
}
