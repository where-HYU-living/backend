package com.chatatda.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.ErrorCode;
import com.chatatda.api.support.IntegrationTest;
import com.chatatda.api.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.ResultActions;

class GoogleAuthApiTest extends IntegrationTest {

    @MockitoBean
    private GoogleTokenVerifier verifier;

    @Autowired
    private UserRepository users;

    private ResultActions login(String token) throws Exception {
        return mvc.perform(post("/api/v1/auth/google").contentType(APPLICATION_JSON).content("{\"idToken\":\"" + token + "\"}"));
    }

    @Test
    void hanyangAccount_issuesToken_andCreatesUserOnce() throws Exception {
        when(verifier.verify(anyString())).thenReturn(new GoogleIdentity("Student@Hanyang.ac.kr", true, "hanyang.ac.kr"));
        login("t").andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.expiresIn").value(604800))
                .andExpect(jsonPath("$.user.email").value("student@hanyang.ac.kr"));
        login("t").andExpect(status().isOk());
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void otherDomain_400() throws Exception {
        when(verifier.verify(anyString())).thenReturn(new GoogleIdentity("a@gmail.com", true, null));
        login("t").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_EMAIL_DOMAIN"));
        assertThat(users.count()).isZero();
    }

    @Test
    void lookalikeDomainsAndWrongHd_400() throws Exception {
        for (GoogleIdentity id : new GoogleIdentity[] {
            new GoogleIdentity("a@hanyang.ac.kr.evil.com", true, null),
            new GoogleIdentity("a@mail.hanyang.ac.kr", true, null),
            new GoogleIdentity("a@hanyang.ac.kr", true, "evil.com"),
        }) {
            when(verifier.verify(anyString())).thenReturn(id);
            login("t").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_EMAIL_DOMAIN"));
        }
    }

    @Test
    void unverifiedEmail_401() throws Exception {
        when(verifier.verify(anyString())).thenReturn(new GoogleIdentity("a@hanyang.ac.kr", false, "hanyang.ac.kr"));
        login("t").andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void invalidToken_401_andBlankBody_400() throws Exception {
        when(verifier.verify(anyString())).thenThrow(new ApiException(ErrorCode.UNAUTHORIZED, "x"));
        login("bad").andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/auth/google").contentType(APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
