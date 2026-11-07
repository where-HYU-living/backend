package com.chatatda.api.favorite;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatatda.api.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

class FavoriteApiTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    long userId;

    @BeforeEach
    void user() {
        userId = jdbc.queryForObject(
                "insert into users (email, nickname, created_at) values ('f@hanyang.ac.kr', 'f', now()) returning id", Long.class);
    }

    private ResultActions add(String type, long targetId) throws Exception {
        return mvc.perform(post("/api/v1/me/favorites").header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"" + type + "\",\"targetId\":" + targetId + "}"));
    }

    private ResultActions list(String query) throws Exception {
        return mvc.perform(get("/api/v1/me/favorites" + query).header("Authorization", bearer(userId)));
    }

    private long housingWithUnits() {
        long id = jdbc.queryForObject("""
                insert into housing (name, address, lat, lng, updated_at) values ('왕십리역 청아진', '서울', 37.565, 127.035, ?)
                returning id""", Long.class, Timestamp.from(Instant.parse("2026-11-07T00:00:00Z")));
        jdbc.update("insert into housing_unit_types (housing_id, name, deposit, rent, mgmt_fee) values (?, '18', 60000000, 620000, 84300)", id);
        jdbc.update("insert into housing_unit_types (housing_id, name, deposit, rent, mgmt_fee) values (?, '25', 60000000, 700000, 84300)", id);
        return id;
    }

    @Test
    void add_list_remove() throws Exception {
        add("LISTING", 201).andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("LISTING"))
                .andExpect(jsonPath("$.targetId").value(201))
                .andExpect(jsonPath("$.name").value("테스트 행당동 분리형"))
                .andExpect(jsonPath("$.convertedRent").value(680_000))
                .andExpect(jsonPath("$.grade", notNullValue()))
                .andExpect(jsonPath("$.createdAt").value("2026-11-07T00:00:00Z"));

        list("").andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].lat").value(37.558))
                .andExpect(jsonPath("$[0].walkMin", nullValue()));

        mvc.perform(delete("/api/v1/me/favorites/LISTING/201").header("Authorization", bearer(userId)))
                .andExpect(status().isNoContent());
        list("").andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void duplicate_409() throws Exception {
        add("LISTING", 201).andExpect(status().isCreated());
        add("LISTING", 201).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ALREADY_EXISTS"));
    }

    @Test
    void delete_missing_204() throws Exception {
        mvc.perform(delete("/api/v1/me/favorites/DORM/107").header("Authorization", bearer(userId)))
                .andExpect(status().isNoContent());
    }

    @Test
    void unknownTarget_404() throws Exception {
        add("LISTING", 999_999).andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        add("HOUSING", 999_999).andExpect(status().isNotFound());
        add("DORM", 999).andExpect(status().isNotFound());
        add("TRANSFER", 1).andExpect(status().isNotFound());
    }

    @Test
    void invalidBody_400() throws Exception {
        mvc.perform(post("/api/v1/me/favorites").header("Authorization", bearer(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"LISTING\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/me/favorites").header("Authorization", bearer(userId))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"CAT\",\"targetId\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void guest_401() throws Exception {
        mvc.perform(get("/api/v1/me/favorites")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/me/favorites").contentType(MediaType.APPLICATION_JSON)
                .content("{\"type\":\"LISTING\",\"targetId\":201}")).andExpect(status().isUnauthorized());
    }

    @Test
    void listingShowsFavorited() throws Exception {
        add("LISTING", 201).andExpect(status().isCreated());

        mvc.perform(get("/api/v1/listings").header("Authorization", bearer(userId)))
                .andExpect(jsonPath("$.items[?(@.id == 201)].favorited", contains(true)))
                .andExpect(jsonPath("$.items[?(@.id == 202)].favorited", contains(false)));
        mvc.perform(get("/api/v1/listings/201").header("Authorization", bearer(userId)))
                .andExpect(jsonPath("$.favorited").value(true));
        mvc.perform(get("/api/v1/listings"))
                .andExpect(jsonPath("$.items[?(@.id == 201)].favorited", contains(false)));
    }

    @Test
    void dormFavorite_gradeNull_cheapestRoom() throws Exception {
        add("DORM", 107).andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("테스트 기숙사"))
                .andExpect(jsonPath("$.grade", nullValue()))
                .andExpect(jsonPath("$.convertedRent").value(250_250))
                .andExpect(jsonPath("$.lat").value(37.5585));
    }

    @Test
    void housingFavorite_cheapestUnit() throws Exception {
        long housingId = housingWithUnits();

        add("HOUSING", housingId).andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("왕십리역 청아진"))
                .andExpect(jsonPath("$.grade", nullValue()))
                .andExpect(jsonPath("$.convertedRent").value(1_004_300));
    }

    @Test
    void typeFilter_andNewestFirst() throws Exception {
        add("LISTING", 201).andExpect(status().isCreated());
        clock.advance(java.time.Duration.ofMinutes(1));
        add("DORM", 107).andExpect(status().isCreated());

        list("").andExpect(jsonPath("$[*].type", contains("DORM", "LISTING")));
        list("?type=DORM").andExpect(jsonPath("$[*].targetId", contains(107)));
    }

    @Test
    void missingTarget_skippedInList() throws Exception {
        long housingId = housingWithUnits();
        add("HOUSING", housingId).andExpect(status().isCreated());
        add("DORM", 107).andExpect(status().isCreated());

        jdbc.update("delete from housing where id = ?", housingId);

        list("").andExpect(jsonPath("$[*].type", contains("DORM")));
    }

    @Test
    void walkMin_fromFirstProfileBuilding() throws Exception {
        jdbc.update("insert into user_buildings (user_id, position, building_id) values (?, 0, 101)", userId);

        add("DORM", 107).andExpect(jsonPath("$.walkMin").value(7));
    }
}
