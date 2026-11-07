package com.chatatda.api.housing;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatatda.api.support.IntegrationTest;
import java.time.LocalDate;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class HousingApiTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    long cheongajin;
    long labom;
    long userId;

    @BeforeEach
    void data() {
        cheongajin = HousingFixtures.housing(jdbc, "왕십리역 청아진", 37.5651, 127.0348);
        HousingFixtures.unit(jdbc, cheongajin, "18타입(18.22)", 18.22, 60_000_000, 620_000, 84_300L);
        HousingFixtures.unit(jdbc, cheongajin, "18A타입(18.22)", 18.22, 60_000_000, 620_000, 84_300L);
        labom = HousingFixtures.housing(jdbc, "왕십리역 라봄성동", 37.5634, 127.0420);
        HousingFixtures.unit(jdbc, labom, "19A", 19.63, 114_000_000, 490_000, 90_550L);
        HousingFixtures.unit(jdbc, labom, "20", 20.0, 119_000_000, 510_000, 93_490L);

        HousingFixtures.announcement(jdbc, cheongajin, "청아진 최초모집", false, Map.of(
                EventType.NOTICE, LocalDate.of(2026, 11, 1),
                EventType.APPLY_START, LocalDate.of(2026, 11, 5),
                EventType.APPLY_END, LocalDate.of(2026, 11, 10),
                EventType.RESULT, LocalDate.of(2026, 11, 20)));
        HousingFixtures.announcement(jdbc, labom, "라봄성동 추가모집", true, Map.of(
                EventType.NOTICE, LocalDate.of(2026, 11, 7),
                EventType.APPLY_START, LocalDate.of(2026, 11, 12),
                EventType.APPLY_END, LocalDate.of(2026, 11, 14)));
        HousingFixtures.announcement(jdbc, labom, "라봄성동 지난 모집", true, Map.of(
                EventType.APPLY_START, LocalDate.of(2026, 9, 18),
                EventType.APPLY_END, LocalDate.of(2026, 9, 21),
                EventType.RESULT, LocalDate.of(2026, 9, 22)));

        userId = jdbc.queryForObject(
                "insert into users (email, nickname, created_at) values ('h@hanyang.ac.kr', 'h', now()) returning id", Long.class);
    }

    @Test
    void housing_recruitingFlag() throws Exception {
        mvc.perform(get("/api/v1/housing"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.id == " + cheongajin + ")].recruiting", contains(true)))
                .andExpect(jsonPath("$[?(@.id == " + labom + ")].recruiting", contains(false)));
    }

    @Test
    void housing_nextEvent_includesToday() throws Exception {
        mvc.perform(get("/api/v1/housing"))
                .andExpect(jsonPath("$[?(@.id == " + labom + ")].nextEvent.date", contains("2026-11-07")))
                .andExpect(jsonPath("$[?(@.id == " + labom + ")].nextEvent.type", contains("NOTICE")))
                .andExpect(jsonPath("$[?(@.id == " + cheongajin + ")].nextEvent.date", contains("2026-11-10")))
                .andExpect(jsonPath("$[?(@.id == " + cheongajin + ")].nextEvent.type", contains("APPLY_END")));
    }

    @Test
    void housing_minConvertedRent_includesMgmtFee() throws Exception {
        mvc.perform(get("/api/v1/housing"))
                .andExpect(jsonPath("$[?(@.id == " + cheongajin + ")].minConvertedRent", contains(1_004_300)))
                .andExpect(jsonPath("$[?(@.id == " + labom + ")].minConvertedRent", contains(1_150_550)))
                .andExpect(jsonPath("$[*].walkMin", everyItem(nullValue())));
    }

    @Test
    void housing_filters() throws Exception {
        mvc.perform(get("/api/v1/housing").param("maxRent", "500000"))
                .andExpect(jsonPath("$[*].id", contains((int) labom)));
        mvc.perform(get("/api/v1/housing").param("maxDeposit", "100000000"))
                .andExpect(jsonPath("$[*].id", contains((int) cheongajin)));
        mvc.perform(get("/api/v1/housing").param("maxRent", "500000").param("maxDeposit", "100000000"))
                .andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/v1/housing").param("bbox", "127.040,37.560,127.045,37.565"))
                .andExpect(jsonPath("$[*].id", contains((int) labom)));
    }

    @Test
    void housing_walkFilter() throws Exception {
        mvc.perform(get("/api/v1/housing").param("buildingId", "101").param("maxWalkMin", "10"))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].walkMin", everyItem(is(7))));
        mvc.perform(get("/api/v1/housing").param("buildingId", "101").param("maxWalkMin", "5"))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void housing_detail() throws Exception {
        mvc.perform(get("/api/v1/housing/" + cheongajin).param("buildingId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("왕십리역 청아진"))
                .andExpect(jsonPath("$.contact").value("02-0000-0000"))
                .andExpect(jsonPath("$.walkMin").value(7))
                .andExpect(jsonPath("$.unitTypes", hasSize(2)))
                .andExpect(jsonPath("$.unitTypes[0].name").value("18타입(18.22)"))
                .andExpect(jsonPath("$.unitTypes[0].exclusiveAreaM2").value(18.22))
                .andExpect(jsonPath("$.unitTypes[0].convertedRent").value(1_004_300))
                .andExpect(jsonPath("$.unitTypes[0].floorplanId").value(nullValue()));
    }

    @Test
    void housing_unknown404() throws Exception {
        mvc.perform(get("/api/v1/housing/999999")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void announcements_statusComputed_andFilters() throws Exception {
        mvc.perform(get("/api/v1/housing/announcements"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[*].status", containsInAnyOrder("OPEN", "UPCOMING", "ANNOUNCED")));
        mvc.perform(get("/api/v1/housing/announcements").param("status", "OPEN"))
                .andExpect(jsonPath("$[*].title", contains("청아진 최초모집")))
                .andExpect(jsonPath("$[0].events[*].type", contains("NOTICE", "APPLY_START", "APPLY_END", "RESULT")));
        mvc.perform(get("/api/v1/housing/announcements").param("housingId", String.valueOf(labom)))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].housingId", everyItem(is((int) labom))));
    }

    @Test
    void subscribe_idempotent_and_subscribedFlag() throws Exception {
        mvc.perform(post("/api/v1/housing/" + labom + "/subscribe").header("Authorization", bearer(userId)))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/housing/" + labom + "/subscribe").header("Authorization", bearer(userId)))
                .andExpect(status().isNoContent());
        Assertions.assertThat(jdbc.queryForObject("select count(*) from housing_subscriptions", Integer.class)).isEqualTo(1);

        mvc.perform(get("/api/v1/housing").header("Authorization", bearer(userId)))
                .andExpect(jsonPath("$[?(@.id == " + labom + ")].subscribed", contains(true)))
                .andExpect(jsonPath("$[?(@.id == " + cheongajin + ")].subscribed", contains(false)));
        mvc.perform(get("/api/v1/housing"))
                .andExpect(jsonPath("$[*].subscribed", everyItem(is(false))));

        mvc.perform(delete("/api/v1/housing/" + labom + "/subscribe").header("Authorization", bearer(userId)))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/v1/housing/" + labom + "/subscribe").header("Authorization", bearer(userId)))
                .andExpect(status().isNoContent());
        Assertions.assertThat(jdbc.queryForObject("select count(*) from housing_subscriptions", Integer.class)).isZero();
    }

    @Test
    void favoritedFlag_listAndDetail() throws Exception {
        jdbc.update("insert into favorites (user_id, type, target_id, created_at) values (?, 'HOUSING', ?, now())", userId, labom);

        mvc.perform(get("/api/v1/housing").header("Authorization", bearer(userId)))
                .andExpect(jsonPath("$[?(@.id == " + labom + ")].favorited", contains(true)))
                .andExpect(jsonPath("$[?(@.id == " + cheongajin + ")].favorited", contains(false)));
        mvc.perform(get("/api/v1/housing/" + labom).header("Authorization", bearer(userId)))
                .andExpect(jsonPath("$.favorited").value(true));
        mvc.perform(get("/api/v1/housing/" + labom))
                .andExpect(jsonPath("$.favorited").value(false));
    }

    @Test
    void subscribe_guest401() throws Exception {
        mvc.perform(post("/api/v1/housing/" + labom + "/subscribe")).andExpect(status().isUnauthorized());
    }

    @Test
    void subscribe_unknownHousing404() throws Exception {
        mvc.perform(post("/api/v1/housing/999999/subscribe").header("Authorization", bearer(userId)))
                .andExpect(status().isNotFound());
    }

    @Test
    void subscriberIds_and_eventsOn() {
        jdbc.update("insert into housing_subscriptions (user_id, housing_id, created_at) values (?, ?, now())", userId, labom);

        Assertions.assertThat(housingService.subscriberIds(labom)).containsExactly(userId);
        Assertions.assertThat(housingService.eventsOn(LocalDate.of(2026, 11, 10)))
                .extracting(HousingService.EventOn::housingId, HousingService.EventOn::type)
                .containsExactly(Assertions.tuple(cheongajin, EventType.APPLY_END));
    }

    @Autowired
    HousingService housingService;
}
