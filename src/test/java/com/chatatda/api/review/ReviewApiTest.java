package com.chatatda.api.review;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatatda.api.support.IntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

class ReviewApiTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    long userId;

    @BeforeEach
    void user() {
        userId = jdbc.queryForObject(
                "insert into users (email, nickname, created_at) values ('r@hanyang.ac.kr', '리뷰어', now()) returning id", Long.class);
    }

    private static String body(String type, long id, int rating, int noise, String text, String pin, String floorplanId) {
        return """
                {"targetType": "%s", "targetId": %d, "rating": %d,
                 "scores": {"noise": %d, "light": 4, "bugs": 3, "landlord": 5, "depositReturn": 4},
                 "text": %s, "pin": %s, "floorplanId": %s}"""
                .formatted(type, id, rating, noise, text == null ? "null" : "\"" + text + "\"", pin, floorplanId);
    }

    private ResultActions create(String json) throws Exception {
        return mvc.perform(post("/api/v1/reviews").header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions list(String type, long id) throws Exception {
        return mvc.perform(get("/api/v1/reviews").param("targetType", type).param("targetId", String.valueOf(id)));
    }

    @Test
    void create_and_list() throws Exception {
        create(body("LISTING", 201, 4, 3, "방음이 약해요", "null", "null"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.rating").value(4))
                .andExpect(jsonPath("$.scores.noise").value(3))
                .andExpect(jsonPath("$.scores.depositReturn").value(4))
                .andExpect(jsonPath("$.text").value("방음이 약해요"))
                .andExpect(jsonPath("$.authorNickname").value("리뷰어"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-07T00:00:00Z"));

        list("LISTING", 201)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.average").value(4.0))
                .andExpect(jsonPath("$.items[0].authorNickname").value("리뷰어"));
    }

    @Test
    void ratingOutOfRange_400() throws Exception {
        create(body("LISTING", 201, 6, 0, "x", "null", "null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.rating").exists())
                .andExpect(jsonPath("$.details['scores.noise']").exists());
    }

    @Test
    void textTooLong_400() throws Exception {
        create(body("LISTING", 201, 4, 3, "가".repeat(501), "null", "null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.text").exists());
    }

    @Test
    void emptyText_ok_butNullText_400() throws Exception {
        create(body("LISTING", 201, 5, 5, "", "null", "null")).andExpect(status().isCreated());
        create(body("LISTING", 201, 5, 5, null, "null", "null"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.text").exists());
    }

    @Test
    void empty_averageNull() throws Exception {
        list("LISTING", 202)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.average", nullValue()))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void pinOptional() throws Exception {
        create(body("LISTING", 201, 4, 3, "핀 없음", "null", "null"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pin", nullValue()))
                .andExpect(jsonPath("$.floorplanId", nullValue()));
    }

    @Test
    void pin_withFloorplan_roundTrip() throws Exception {
        create(body("DORM", 107, 3, 2, "여기 곰팡이", "[0.4, 1.2, 2.1]", "1"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pin", contains(0.4, 1.2, 2.1)))
                .andExpect(jsonPath("$.floorplanId").value(1));

        list("DORM", 107)
                .andExpect(jsonPath("$.items[0].pin", contains(0.4, 1.2, 2.1)))
                .andExpect(jsonPath("$.items[0].floorplanId").value(1));
    }

    @Test
    void pin_withoutFloorplan_accepted() throws Exception {
        create(body("DORM", 107, 3, 2, "방 번호 없는 핀", "[1.0, 0.0, 1.0]", "null"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.floorplanId", nullValue()));
    }

    @Test
    void pin_wrongLength_400() throws Exception {
        create(body("DORM", 107, 3, 2, "x", "[1.0, 2.0]", "1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.pin").exists());
    }

    @Test
    void unknownFloorplan_400() throws Exception {
        create(body("DORM", 107, 3, 2, "x", "[1.0, 0.0, 1.0]", "99999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.floorplanId").exists());
    }

    @Test
    void unknownTarget_404() throws Exception {
        create(body("LISTING", 999_999, 4, 3, "x", "null", "null")).andExpect(status().isNotFound());
        create(body("HOUSING", 999_999, 4, 3, "x", "null", "null")).andExpect(status().isNotFound());
        create(body("DORM", 999, 4, 3, "x", "null", "null")).andExpect(status().isNotFound());
    }

    @Test
    void guest_401() throws Exception {
        mvc.perform(post("/api/v1/reviews").contentType(MediaType.APPLICATION_JSON)
                .content(body("LISTING", 201, 4, 3, "x", "null", "null"))).andExpect(status().isUnauthorized());
    }

    @Test
    void average_rounded_newestFirst_andTargetsSeparate() throws Exception {
        create(body("LISTING", 201, 4, 3, "첫째", "null", "null"));
        clock.advance(Duration.ofMinutes(1));
        create(body("LISTING", 201, 5, 3, "둘째", "null", "null"));
        clock.advance(Duration.ofMinutes(1));
        create(body("LISTING", 201, 4, 3, "셋째", "null", "null"));
        create(body("LISTING", 202, 1, 1, "다른 매물", "null", "null"));

        list("LISTING", 201)
                .andExpect(jsonPath("$.count").value(3))
                .andExpect(jsonPath("$.average").value(4.3))
                .andExpect(jsonPath("$.items[*].text", contains("셋째", "둘째", "첫째")));
        list("LISTING", 202).andExpect(jsonPath("$.count").value(1));
    }

    @Test
    void missingParams_400() throws Exception {
        mvc.perform(get("/api/v1/reviews").param("targetType", "LISTING")).andExpect(status().isBadRequest());
    }
}
