package com.moyeobom.guest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.moyeobom.support.IntegrationTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class GuestApiTest extends IntegrationTestSupport {

    @Test
    void 발급받은_게스트로_내_정보를_조회한다() throws Exception {
        String guestId = issueGuest();

        mockMvc.perform(get("/api/v1/guests/me").header("X-Guest-Id", guestId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestId").value(guestId))
                .andExpect(jsonPath("$.hasOpenSprint").value(false));
    }

    @Test
    void 헤더가_없거나_모르는_게스트면_401() throws Exception {
        mockMvc.perform(get("/api/v1/guests/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("GUEST_NOT_FOUND"));

        mockMvc.perform(get("/api/v1/guests/me").header("X-Guest-Id", "not-a-uuid"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/guests/me").header("X-Guest-Id", "3f1c2a8e-0000-4000-8000-000000000000"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void 없는_주소는_404_허용하지_않는_방식은_405() throws Exception {
        String guestId = issueGuest();
        mockMvc.perform(get("/api/v1/nowhere").header("X-Guest-Id", guestId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        mockMvc.perform(patch("/api/v1/guests/me").header("X-Guest-Id", guestId))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void 휴식_알림은_기본으로_꺼져_있고_보낸_항목만_바뀐다() throws Exception {
        String guestId = issueGuest();

        mockMvc.perform(get("/api/v1/guests/me/setting").header("X-Guest-Id", guestId))
                .andExpect(jsonPath("$.breakAlertEnabled").value(false))
                .andExpect(jsonPath("$.breakAlertMinutes").value(50));

        mockMvc.perform(patch("/api/v1/guests/me/setting").header("X-Guest-Id", guestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"breakAlertEnabled\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.breakAlertEnabled").value(true))
                .andExpect(jsonPath("$.breakAlertMinutes").value(50));
    }

    @Test
    void 알림_시간이_0이하면_필드와_함께_400() throws Exception {
        String guestId = issueGuest();

        mockMvc.perform(patch("/api/v1/guests/me/setting").header("X-Guest-Id", guestId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"breakAlertMinutes\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.errors[0].field").value("breakAlertMinutes"));
    }
}
