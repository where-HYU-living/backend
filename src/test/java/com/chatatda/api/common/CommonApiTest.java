package com.chatatda.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatatda.api.support.IntegrationTest;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

class CommonApiTest extends IntegrationTest {

    @Test
    void health_ok() throws Exception {
        mvc.perform(get("/api/v1/health")).andExpect(status().isOk());
    }

    @Test
    void notFound_usesErrorFormat() throws Exception {
        mvc.perform(get("/api/v1/__none"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void cors_allowsLocalFront() throws Exception {
        mvc.perform(options("/api/v1/health")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }

    @Test
    void clock_isSeoul() {
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Seoul"));
    }

    @Test
    void invalidEnumParam_400() throws Exception {
        validationFailed(mvc.perform(get("/api/v1/__probe/enum").param("level", "FOO")))
                .andExpect(jsonPath("$.details.level").exists());
    }

    @Test
    void missingRequiredParam_400() throws Exception {
        validationFailed(mvc.perform(get("/api/v1/__probe/required")))
                .andExpect(jsonPath("$.details.q").exists());
    }

    @Test
    void brokenJsonBody_400() throws Exception {
        validationFailed(mvc.perform(post("/api/v1/__probe/body")
                .header("Authorization", bearer(1)).contentType(APPLICATION_JSON).content("{ not json")));
    }

    @Test
    void invalidBodyField_400_withFieldDetails() throws Exception {
        validationFailed(mvc.perform(post("/api/v1/__probe/body")
                        .header("Authorization", bearer(1)).contentType(APPLICATION_JSON).content("{\"name\":\"\"}")))
                .andExpect(jsonPath("$.details.name").exists());
    }

    @Test
    void wrongMethod_405_inErrorFormat() throws Exception {
        mvc.perform(post("/api/v1/health").header("Authorization", bearer(1)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void unexpectedException_500_withoutLeakingInternals() throws Exception {
        mvc.perform(get("/api/v1/__probe/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("비밀"))));
    }

    private ResultActions validationFailed(ResultActions result) throws Exception {
        return result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.message").isString());
    }
}
