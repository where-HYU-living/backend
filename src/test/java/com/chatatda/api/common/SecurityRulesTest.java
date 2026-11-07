package com.chatatda.api.common;

import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatatda.api.support.IntegrationTest;
import com.chatatda.api.user.User;
import com.chatatda.api.user.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

class SecurityRulesTest extends IntegrationTest {

    @Autowired
    UserRepository users;

    @Test
    void guest_canBrowse() throws Exception {
        mvc.perform(get("/api/v1/listings")).andExpect(status().isOk());
        for (String path : new String[] {"/api/v1/housing", "/api/v1/search?q=행당"}) {
            int status = mvc.perform(get(path)).andReturn().getResponse().getStatus();
            org.assertj.core.api.Assertions.assertThat(status).as(path).isNotIn(401, 403);
        }
    }

    @Test
    void guest_meIs401() throws Exception {
        mvc.perform(get("/api/v1/me/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").isString());
    }

    @Test
    void guest_writeIs401() throws Exception {
        mvc.perform(post("/api/v1/me/favorites").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/transfers").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void guest_canCreateInvite() throws Exception {
        int status = mvc.perform(post("/api/v1/invites").contentType(APPLICATION_JSON).content("{\"playerId\":\"p_8f2a\"}"))
                .andReturn().getResponse().getStatus();
        org.assertj.core.api.Assertions.assertThat(status).isNotIn(401, 403);
    }

    @Test
    void validToken_passesOnMe() throws Exception {
        long userId = users.save(new User("valid@hanyang.ac.kr", "한양0007", clock.instant())).getId();
        mvc.perform(get("/api/v1/me/profile").header("Authorization", bearer(userId)))
                .andExpect(status().isOk());
    }

    @Test
    void expiredToken_onPublicGet_isTreatedAsGuest() throws Exception {
        String token = bearer(7);
        clock.advance(Duration.ofDays(8));
        mvc.perform(get("/api/v1/health").header("Authorization", token)).andExpect(status().isOk());
    }

    @Test
    void expiredToken_onMe_is401() throws Exception {
        String token = bearer(7);
        clock.advance(Duration.ofDays(8));
        mvc.perform(get("/api/v1/me/profile").header("Authorization", token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void malformedToken_onMe_is401() throws Exception {
        mvc.perform(get("/api/v1/me/profile").header("Authorization", "Bearer abc"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/me/profile").header("Authorization", "Basic abc"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void cors_preflightOnProtectedPath_isAllowed() throws Exception {
        MvcResult result = mvc.perform(options("/api/v1/me/profile")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andReturn();
        org.assertj.core.api.Assertions.assertThat(result.getResponse().getHeader("Access-Control-Allow-Headers"))
                .isNotNull();
    }

    @Test
    void cors_headerPresentOn401() throws Exception {
        mvc.perform(get("/api/v1/me/profile").header("Origin", "http://localhost:5173"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}
