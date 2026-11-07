package com.chatatda.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatatda.api.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

class AuthApiTest extends IntegrationTest {

    private static final String EMAIL = "student@hanyang.ac.kr";

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void send_rejectsOtherDomain() throws Exception {
        send("a@gmail.com")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL_DOMAIN"));
        assertThat(mail.sentCount()).isZero();
    }

    @Test
    void send_rejectsLookalikeDomains() throws Exception {
        for (String email : new String[] {"a@hanyang.ac.kr.evil.com", "a@mail.hanyang.ac.kr", "a@xhanyang.ac.kr"}) {
            send(email).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_EMAIL_DOMAIN"));
        }
    }

    @Test
    void send_invalidFormat_400() throws Exception {
        send("not-an-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.email").exists());
    }

    @Test
    void send_then_verify_issuesToken() throws Exception {
        send(EMAIL).andExpect(status().isNoContent());
        String code = mail.lastCode(EMAIL);
        assertThat(code).matches("\\d{6}");

        JsonNode body = json(verify(EMAIL, code)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.expiresIn").value(604800))
                .andExpect(jsonPath("$.user.email").value(EMAIL))
                .andExpect(jsonPath("$.user.nickname").value(matchesPattern("^한양\\d{4}$"))));

        long userId = body.at("/user/id").asLong();
        assertThat(jwtProvider.parse(body.get("accessToken").asText())).contains(userId);
    }

    @Test
    void verify_wrongCode_400() throws Exception {
        send(EMAIL);
        verify(EMAIL, wrongCode()).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CODE_MISMATCH"));
    }

    @Test
    void verify_withoutSend_400() throws Exception {
        verify(EMAIL, "123456").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CODE_MISMATCH"));
    }

    @Test
    void verify_invalidCodeFormat_400_andDoesNotCountAsAttempt() throws Exception {
        send(EMAIL);
        for (int i = 0; i < 6; i++) {
            verify(EMAIL, "12").andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        verify(EMAIL, mail.lastCode(EMAIL)).andExpect(status().isOk());
    }

    @Test
    void verify_after10min_expired() throws Exception {
        send(EMAIL);
        clock.advance(Duration.ofSeconds(601));
        verify(EMAIL, mail.lastCode(EMAIL)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CODE_EXPIRED"));
    }

    @Test
    void verify_justBefore10min_ok() throws Exception {
        send(EMAIL);
        clock.advance(Duration.ofSeconds(599));
        verify(EMAIL, mail.lastCode(EMAIL)).andExpect(status().isOk());
    }

    @Test
    void resend_within60s_429() throws Exception {
        send(EMAIL).andExpect(status().isNoContent());
        clock.advance(Duration.ofSeconds(30));
        send(EMAIL).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        assertThat(mail.sentCount()).isEqualTo(1);
    }

    @Test
    void resend_after60s_replacesOldCode() throws Exception {
        send(EMAIL);
        String oldCode = mail.lastCode(EMAIL);
        clock.advance(Duration.ofSeconds(61));
        send(EMAIL).andExpect(status().isNoContent());
        String newCode = mail.lastCode(EMAIL);

        if (!oldCode.equals(newCode)) {
            verify(EMAIL, oldCode).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CODE_MISMATCH"));
        }
        verify(EMAIL, newCode).andExpect(status().isOk());
    }

    @Test
    void fiveWrongAttempts_discardsCode() throws Exception {
        send(EMAIL);
        String code = mail.lastCode(EMAIL);
        String wrong = wrongCode(code);

        for (int i = 1; i <= 4; i++) {
            verify(EMAIL, wrong).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CODE_MISMATCH"));
        }
        verify(EMAIL, wrong).andExpect(status().isTooManyRequests()).andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
        verify(EMAIL, code).andExpect(status().isTooManyRequests());

        clock.advance(Duration.ofSeconds(61));
        send(EMAIL).andExpect(status().isNoContent());
        verify(EMAIL, mail.lastCode(EMAIL)).andExpect(status().isOk());
    }

    @Test
    void usedCode_cannotBeReused() throws Exception {
        send(EMAIL);
        String code = mail.lastCode(EMAIL);
        verify(EMAIL, code).andExpect(status().isOk());
        verify(EMAIL, code).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CODE_MISMATCH"));
    }

    @Test
    void sameEmail_isSameUser_caseInsensitive() throws Exception {
        send(EMAIL);
        JsonNode first = json(verify(EMAIL, mail.lastCode(EMAIL)).andExpect(status().isOk()));

        clock.advance(Duration.ofSeconds(61));
        send("Student@Hanyang.AC.kr").andExpect(status().isNoContent());
        JsonNode second = json(verify("STUDENT@hanyang.ac.kr", mail.lastCode(EMAIL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(EMAIL)));

        assertThat(second.at("/user/id").asLong()).isEqualTo(first.at("/user/id").asLong());
        assertThat(second.at("/user/nickname").asText()).isEqualTo(first.at("/user/nickname").asText());
    }

    @Test
    void differentEmails_getDifferentUsers() throws Exception {
        send("a@hanyang.ac.kr");
        long a = json(verify("a@hanyang.ac.kr", mail.lastCode("a@hanyang.ac.kr"))).at("/user/id").asLong();
        send("b@hanyang.ac.kr");
        long b = json(verify("b@hanyang.ac.kr", mail.lastCode("b@hanyang.ac.kr"))).at("/user/id").asLong();
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    void send_mailFailure_502_andNoCooldown() throws Exception {
        mail.failNext();
        send(EMAIL).andExpect(status().isBadGateway()).andExpect(jsonPath("$.code").value("UPSTREAM_FAILED"));

        send(EMAIL).andExpect(status().isNoContent());
        assertThat(mail.sentCount()).isEqualTo(1);
    }

    private ResultActions send(String email) throws Exception {
        return mvc.perform(post("/api/v1/auth/email/send")
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\"}"));
    }

    private ResultActions verify(String email, String code) throws Exception {
        return mvc.perform(post("/api/v1/auth/email/verify")
                .contentType(APPLICATION_JSON)
                .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}"));
    }

    private JsonNode json(ResultActions result) throws Exception {
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private String wrongCode() {
        return wrongCode(mail.lastCode(EMAIL));
    }

    private String wrongCode(String actual) {
        return "000000".equals(actual) ? "111111" : "000000";
    }
}
