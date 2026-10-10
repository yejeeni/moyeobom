package com.moyeobom.sprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.moyeobom.room.service.RoomService;
import com.moyeobom.support.IntegrationTestSupport;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 스프린트 시작 → 집중 → 완료 → 회고 → 이월 → 다음 스프린트까지 한 번에 따라간다.
 */
class SprintFlowTest extends IntegrationTestSupport {

    @Autowired
    RoomService roomService;

    @Test
    void 스프린트_한_바퀴() throws Exception {
        String guest = issueGuest();

        // 1. 계획: 할 일 3개로 시작하고 열람실에 들어간다
        String started = perform(post("/api/v1/sprints"), guest, """
                {"tasks": [{"title": "알고리즘 3문제", "estimatedMinutes": 90},
                           {"title": "자소서 수정", "estimatedMinutes": null},
                           {"title": "영어 단어", "estimatedMinutes": 20}]}
                """).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long algorithm = taskId(started, 0);
        long resume = taskId(started, 1);
        long english = taskId(started, 2);
        String roomId = JsonPath.read(perform(post("/api/v1/rooms"), guest, "{\"virtualSeats\": 8, \"realSeats\": 1}")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.roomId");

        // 2. 집중: 알고리즘 110분(휴식 끼고 두 번), 자소서 10분 후 마무리
        perform(post("/api/v1/tasks/" + algorithm + "/focus"), guest, null);
        clock.advance(Duration.ofMinutes(60));
        perform(post("/api/v1/focus/stop"), guest, "{\"reason\": \"BREAK\"}");
        clock.advance(Duration.ofMinutes(10));
        perform(post("/api/v1/tasks/" + algorithm + "/focus"), guest, null);
        clock.advance(Duration.ofMinutes(50));
        perform(post("/api/v1/tasks/" + algorithm + "/complete"), guest, null)
                .andExpect(jsonPath("$.task.actualSeconds").value(6600));

        perform(post("/api/v1/tasks/" + resume + "/focus"), guest, null);
        clock.advance(Duration.ofMinutes(10));

        // 3. 회고: 진행 중인 집중을 끝내고 조회한다
        perform(post("/api/v1/focus/stop"), guest, "{\"reason\": \"STOPPED\"}");
        perform(get("/api/v1/sprints/current/review"), guest, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFocusSeconds").value(7200))
                .andExpect(jsonPath("$.completedCount").value(1))
                .andExpect(jsonPath("$.estimatedSecondsOfDone").value(5400))
                .andExpect(jsonPath("$.actualSecondsOfDone").value(6600))
                .andExpect(jsonPath("$.tasks[0].diffSeconds").value(1200))
                .andExpect(jsonPath("$.tasks[1].estimatedSeconds").isEmpty())
                .andExpect(jsonPath("$.tasks[1].diffSeconds").isEmpty())
                .andExpect(jsonPath("$.tasks[1].actualSeconds").value(600));

        // 완료한 할 일은 이월·닫기를 고를 수 없다
        perform(post("/api/v1/sprints/current/close"), guest,
                "{\"decisions\": [{\"taskId\": " + algorithm + ", \"action\": \"DROP\"}]}")
                .andExpect(status().isBadRequest());

        // 4. 마무리: 자소서는 기본값(이월), 영어 단어는 닫는다
        perform(post("/api/v1/sprints/current/close"), guest,
                "{\"decisions\": [{\"taskId\": " + english + ", \"action\": \"DROP\"}]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.carriedCount").value(1))
                .andExpect(jsonPath("$.droppedCount").value(1))
                .andExpect(jsonPath("$.refreshMessage", notNullValue()));

        assertThat(roomService.findRoomId(guestIdOf(guest))).isEmpty();
        perform(get("/api/v1/sprints/current"), guest, null).andExpect(jsonPath("$.sprint").isEmpty());
        perform(get("/api/v1/sprints/current/review"), guest, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPRINT_NOT_FOUND"));

        // 5. 다음 계획: 이월 할 일이 이전 누적 시간과 함께 미리 채워진다
        perform(get("/api/v1/sprints/carryover"), guest, null)
                .andExpect(jsonPath("$.tasks", hasSize(1)))
                .andExpect(jsonPath("$.tasks[0].taskId").value(resume))
                .andExpect(jsonPath("$.tasks[0].title").value("자소서 수정"))
                .andExpect(jsonPath("$.tasks[0].cumulativeSeconds").value(600));

        // 6. 이월: 남은 작업 기준 예상 시간을 다시 받고, 실제 시간은 0부터 잰다
        String next = perform(post("/api/v1/sprints"), guest, """
                {"tasks": [{"title": "포트폴리오 정리"}],
                 "carriedTasks": [{"fromTaskId": %d, "estimatedMinutes": 30}]}
                """.formatted(resume))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sprint.tasks[0].title").value("자소서 수정"))
                .andExpect(jsonPath("$.sprint.tasks[0].carriedFromTaskId").value(resume))
                .andExpect(jsonPath("$.sprint.tasks[0].estimatedMinutes").value(30))
                .andExpect(jsonPath("$.sprint.tasks[0].actualSeconds").value(0))
                .andExpect(jsonPath("$.sprint.tasks[0].cumulativeSeconds").value(600))
                .andReturn().getResponse().getContentAsString();
        long carried = taskId(next, 0);

        perform(post("/api/v1/tasks/" + carried + "/focus"), guest, null);
        clock.advance(Duration.ofMinutes(5));
        perform(post("/api/v1/tasks/" + carried + "/complete"), guest, null)
                .andExpect(jsonPath("$.task.actualSeconds").value(300))
                .andExpect(jsonPath("$.task.cumulativeSeconds").value(900));

        // 이미 이월한 할 일은 다시 가져갈 수 없다
        perform(get("/api/v1/sprints/carryover"), guest, null).andExpect(jsonPath("$.tasks", hasSize(0)));
    }

    @Test
    void 같은_할_일을_두_번_이월할_수_없다() throws Exception {
        String guest = issueGuest();
        String started = perform(post("/api/v1/sprints"), guest, "{\"tasks\": [{\"title\": \"자소서\"}]}")
                .andReturn().getResponse().getContentAsString();
        long taskId = taskId(started, 0);
        perform(post("/api/v1/sprints/current/close"), guest, "{}").andExpect(status().isOk());

        perform(post("/api/v1/sprints"), guest, """
                {"carriedTasks": [{"fromTaskId": %d}, {"fromTaskId": %d}]}
                """.formatted(taskId, taskId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_ALREADY_CARRIED"));

        perform(post("/api/v1/sprints"), guest, "{\"carriedTasks\": [{\"fromTaskId\": %d}]}".formatted(taskId))
                .andExpect(status().isCreated());
        perform(post("/api/v1/sprints/current/close"), guest, "{}");

        perform(post("/api/v1/sprints"), guest, "{\"carriedTasks\": [{\"fromTaskId\": %d}]}".formatted(taskId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_ALREADY_CARRIED"));
    }

    @Test
    void 다른_게스트의_할_일은_이월할_수_없다() throws Exception {
        String owner = issueGuest();
        String started = perform(post("/api/v1/sprints"), owner, "{\"tasks\": [{\"title\": \"자소서\"}]}")
                .andReturn().getResponse().getContentAsString();
        long taskId = taskId(started, 0);
        perform(post("/api/v1/sprints/current/close"), owner, "{}");

        String other = issueGuest();
        perform(post("/api/v1/sprints"), other, "{\"carriedTasks\": [{\"fromTaskId\": %d}]}".formatted(taskId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));
    }

    private ResultActions perform(MockHttpServletRequestBuilder request,
                                  String guest, String json) throws Exception {
        request.header("X-Guest-Id", guest);
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        return mockMvc.perform(request);
    }

    private Long guestIdOf(String publicId) {
        return jdbcTemplate.queryForObject("select id from guest where public_id = ?", Long.class, publicId);
    }

    private static long taskId(String body, int index) {
        return ((Number) JsonPath.read(body, "$.sprint.tasks[" + index + "].taskId")).longValue();
    }
}
