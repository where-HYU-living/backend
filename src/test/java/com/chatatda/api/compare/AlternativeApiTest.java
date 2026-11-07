package com.chatatda.api.compare;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatatda.api.support.IntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class AlternativeApiTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    long cheongajin;
    long labom;

    @BeforeEach
    void data() {
        cheongajin = housing("왕십리역 청아진");
        unit(cheongajin, "18타입(18.22)", 60_000_000, 620_000, 84_300L);
        unit(cheongajin, "18A타입(18.22)", 60_000_000, 620_000, 84_300L);
        labom = housing("왕십리역 라봄성동");
        unit(labom, "19A", 114_000_000, 490_000, 90_550L);
        unit(labom, "20", 119_000_000, 510_000, 93_490L);
        jdbc.update("""
                insert into listings (id, title, address, dong, lat, lng, deposit, rent, mgmt_fee, area_m2, room_type)
                values (999, '아주 싼 방', '서울 성동구 사근동 1', '사근동', 37.56, 127.04, 0, 200000, 0, 12, 'OPEN')""");
    }

    @AfterEach
    void cleanup() {
        jdbc.update("delete from listings where id = 999");
    }

    private long housing(String name) {
        return jdbc.queryForObject("""
                insert into housing (name, address, lat, lng, updated_at) values (?, ?, 37.56, 127.04, ?) returning id""",
                Long.class, name, "서울 " + name, Timestamp.from(Instant.parse("2026-11-07T00:00:00Z")));
    }

    private void unit(long housingId, String name, long deposit, long rent, Long mgmt) {
        jdbc.update("""
                insert into housing_unit_types (housing_id, name, deposit, rent, mgmt_fee) values (?, ?, ?, ?, ?)""",
                housingId, name, deposit, rent, mgmt);
    }

    @Test
    void alternatives_onlyCheaper_sortedAndDiff() throws Exception {
        mvc.perform(get("/api/v1/listings/202/alternatives").param("buildingId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.housing", hasSize(3)))
                .andExpect(jsonPath("$.housing[*].convertedRent", contains(1_004_300, 1_004_300, 1_150_550)))
                .andExpect(jsonPath("$.housing[*].id", contains((int) cheongajin, (int) cheongajin, (int) labom)))
                .andExpect(jsonPath("$.housing[0].type").value("HOUSING"))
                .andExpect(jsonPath("$.housing[0].name").value("왕십리역 청아진 18타입(18.22)"))
                .andExpect(jsonPath("$.housing[0].monthlyDiff").value(495_700))
                .andExpect(jsonPath("$.housing[0].twoYearDiff").value(495_700 * 24))
                .andExpect(jsonPath("$.housing[0].walkMin").value(7))
                .andExpect(jsonPath("$.dorms", hasSize(3)))
                .andExpect(jsonPath("$.dorms[*].convertedRent", contains(250_250, 284_250, 345_000)))
                .andExpect(jsonPath("$.dorms[*].id", everyItem(is(107))))
                .andExpect(jsonPath("$.dorms[0].type").value("DORM"))
                .andExpect(jsonPath("$.dorms[0].name").value("테스트 기숙사 3인실 (여)"));
    }

    @Test
    void alternatives_cheaperListing_dormsOnly() throws Exception {
        mvc.perform(get("/api/v1/listings/203/alternatives"))
                .andExpect(jsonPath("$.housing", hasSize(0)))
                .andExpect(jsonPath("$.dorms", hasSize(3)))
                .andExpect(jsonPath("$.dorms[0].walkMin").value(org.hamcrest.Matchers.nullValue()));
    }

    @Test
    void alternatives_noneCheaper_empty() throws Exception {
        mvc.perform(get("/api/v1/listings/999/alternatives"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.housing", hasSize(0)))
                .andExpect(jsonPath("$.dorms", hasSize(0)));
    }

    @Test
    void alternatives_unknownListing404() throws Exception {
        mvc.perform(get("/api/v1/listings/123456/alternatives")).andExpect(status().isNotFound());
    }
}
