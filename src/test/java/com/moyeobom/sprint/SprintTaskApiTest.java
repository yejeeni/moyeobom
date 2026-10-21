package com.moyeobom.sprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.moyeobom.support.IntegrationTestSupport;
import java.time.Duration;
import java.time.Instant;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

class SprintTaskApiTest extends IntegrationTestSupport {

    private static final String TWO_TASKS = """
            {"tasks": [{"title": "알고리즘 3문제", "estimatedMinutes": 90},
                       {"title": "  자소서 수정  ", "estimatedMinutes": null}]}
            """;

    @Test
    void 할_일로_스프린트를_시작하면_열린_스프린트가_생긴다() throws Exception {
        String guest = issueGuest();

        startSprint(guest, TWO_TASKS)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sprint.sprintId", notNullValue()))
                .andExpect(jsonPath("$.sprint.tasks", hasSize(2)))
                .andExpect(jsonPath("$.sprint.tasks[1].title").value("자소서 수정"))
                .andExpect(jsonPath("$.sprint.tasks[1].estimatedMinutes").isEmpty())
                .andExpect(jsonPath("$.sprint.tasks[0].status").value("TODO"));

        mockMvc.perform(get("/api/v1/guests/me").header("X-Guest-Id", guest))
                .andExpect(jsonPath("$.hasOpenSprint").value(true));
    }

    @Test
    void 열린_스프린트가_없으면_sprint는_null() throws Exception {
        String guest = issueGuest();

        mockMvc.perform(get("/api/v1/sprints/current").header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sprint").isEmpty());
    }

    @Test
    void 할_일_없이_시작하거나_두_번_시작할_수_없다() throws Exception {
        String guest = issueGuest();

        startSprint(guest, "{\"tasks\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        startSprint(guest, TWO_TASKS).andExpect(status().isCreated());
        startSprint(guest, TWO_TASKS)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SPRINT_ALREADY_OPEN"));
    }

    @Test
    void 할_일은_스프린트마다_50개까지다() throws Exception {
        String guest = issueGuest();

        startSprint(guest, tasksJson(51)).andExpect(status().isBadRequest());

        startSprint(guest, tasksJson(49)).andExpect(status().isCreated());
        addTask(guest, "{\"title\": \"50번째\"}").andExpect(status().isCreated());
        addTask(guest, "{\"title\": \"51번째\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_LIMIT_EXCEEDED"));
    }

    @Test
    void 공부할_시간을_정해_시작하고_연장하면_회고에_계획_시간이_나온다() throws Exception {
        String guest = issueGuest();

        startSprint(guest, "{\"tasks\": [{\"title\": \"알고리즘\"}], \"plannedMinutes\": 20}")
                .andExpect(status().isBadRequest());
        String body = startSprint(guest, "{\"tasks\": [{\"title\": \"알고리즘\"}], \"plannedMinutes\": 120}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sprint.plannedMinutes").value(120))
                .andReturn().getResponse().getContentAsString();
        Instant startedAt = Instant.parse(JsonPath.read(body, "$.sprint.startedAt"));
        Instant endsAt = Instant.parse(JsonPath.read(body, "$.sprint.endsAt"));
        assertThat(Duration.between(startedAt, endsAt)).isEqualTo(Duration.ofMinutes(120));

        // 30분 더
        mockMvc.perform(patch("/api/v1/sprints/current").header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"plannedMinutes\": 150}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sprint.plannedMinutes").value(150));
        mockMvc.perform(get("/api/v1/sprints/current/review").header("X-Guest-Id", guest))
                .andExpect(jsonPath("$.plannedSeconds").value(150 * 60));
    }

    @Test
    void 공부할_시간_없이_시작하면_타임박스가_없다() throws Exception {
        String guest = issueGuest();

        startSprint(guest, TWO_TASKS)
                .andExpect(jsonPath("$.sprint.plannedMinutes").isEmpty())
                .andExpect(jsonPath("$.sprint.endsAt").isEmpty());
        mockMvc.perform(get("/api/v1/sprints/current/review").header("X-Guest-Id", guest))
                .andExpect(jsonPath("$.plannedSeconds").isEmpty());
    }

    @Test
    void 공백만_있는_제목은_저장되지_않는다() throws Exception {
        String guest = issueGuest();
        startSprint(guest, TWO_TASKS);

        addTask(guest, "{\"title\": \"   \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"));

        mockMvc.perform(patch("/api/v1/tasks/" + firstTaskId(guest)).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\": \" \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void 열린_스프린트가_없으면_할_일을_추가할_수_없다() throws Exception {
        String guest = issueGuest();

        addTask(guest, "{\"title\": \"할 일\"}")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SPRINT_NOT_FOUND"));
    }

    @Test
    void 할_일을_추가하고_수정하고_삭제한다() throws Exception {
        String guest = issueGuest();
        startSprint(guest, TWO_TASKS);

        String body = addTask(guest, "{\"title\": \"영어 단어\", \"estimatedMinutes\": 20}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sortOrder").value(3))
                .andExpect(jsonPath("$.hasRecords").value(false))
                .andReturn().getResponse().getContentAsString();
        Integer taskId = JsonPath.read(body, "$.taskId");

        mockMvc.perform(patch("/api/v1/tasks/" + taskId).header("X-Guest-Id", guest)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"estimatedMinutes\": 30}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("영어 단어"))
                .andExpect(jsonPath("$.estimatedMinutes").value(30));

        mockMvc.perform(delete("/api/v1/tasks/" + taskId).header("X-Guest-Id", guest))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/v1/sprints/current").header("X-Guest-Id", guest))
                .andExpect(jsonPath("$.sprint.tasks", hasSize(2)));
    }

    @Test
    void 다른_게스트의_할_일은_404로_숨긴다() throws Exception {
        String owner = issueGuest();
        String other = issueGuest();
        startSprint(owner, TWO_TASKS);
        long taskId = firstTaskId(owner);

        mockMvc.perform(patch("/api/v1/tasks/" + taskId).header("X-Guest-Id", other)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"title\": \"뺏기\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("TASK_NOT_FOUND"));

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/complete").header("X-Guest-Id", other))
                .andExpect(status().isNotFound());
    }

    @Test
    void 할_일을_완료하면_리프레시_문구를_받고_다시_완료할_수_없다() throws Exception {
        String guest = issueGuest();
        startSprint(guest, TWO_TASKS);
        long taskId = firstTaskId(guest);

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/complete").header("X-Guest-Id", guest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.task.status").value("DONE"))
                .andExpect(jsonPath("$.task.completedAt").value("2026-10-05T00:00:00Z"))
                .andExpect(jsonPath("$.refreshMessage", notNullValue()));

        mockMvc.perform(post("/api/v1/tasks/" + taskId + "/complete").header("X-Guest-Id", guest))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("TASK_ALREADY_DONE"));
    }

    private static String tasksJson(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(i -> "{\"title\": \"할 일 " + i + "\"}")
                .collect(Collectors.joining(",", "{\"tasks\": [", "]}"));
    }

    private ResultActions startSprint(String guest, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/sprints").header("X-Guest-Id", guest)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions addTask(String guest, String json) throws Exception {
        return mockMvc.perform(post("/api/v1/sprints/current/tasks").header("X-Guest-Id", guest)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long firstTaskId(String guest) throws Exception {
        String body = mockMvc.perform(get("/api/v1/sprints/current").header("X-Guest-Id", guest))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(body, "$.sprint.tasks[0].taskId")).longValue();
    }
}
