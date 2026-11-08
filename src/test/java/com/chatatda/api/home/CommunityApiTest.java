package com.chatatda.api.home;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

import com.chatatda.api.geo.AddressResult;
import com.chatatda.api.notification.NotificationJob;
import com.chatatda.api.support.IntegrationTest;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** 계약 5-3(검색)·5-8(후기)·5-9(내 집 기록)·5-10(알림함) — 이번에 서버에 새로 만든 엔드포인트 */
class CommunityApiTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    NotificationJob job;

    long userId;

    @BeforeEach
    void user() {
        userId = jdbc.queryForObject(
                "insert into users (email, nickname, created_at) values ('c@hanyang.ac.kr', '한양1234', now()) returning id", Long.class);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b) {
        return b.header("Authorization", bearer(userId));
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder b, String body) {
        return auth(b).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void reviews_create_list_average_and_pin() throws Exception {
        mvc.perform(json(post("/api/v1/reviews"), """
                {"targetType":"LISTING","targetId":201,"rating":4,"scores":{"noise":3,"light":4,"bugs":5,"landlord":4,"depositReturn":5},"text":" 조용해요 ","pin":null}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.authorNickname").value("한양1234"))
                .andExpect(jsonPath("$.text").value("조용해요"))
                .andExpect(jsonPath("$.pin", nullValue()));
        mvc.perform(json(post("/api/v1/reviews"), """
                {"targetType":"LISTING","targetId":201,"rating":1,"scores":{"noise":1,"light":1,"bugs":1,"landlord":1,"depositReturn":1},"text":"여기 곰팡이","pin":[1.2,0.5,3.4]}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pin[2]").value(3.4));

        mvc.perform(get("/api/v1/reviews?targetType=LISTING&targetId=201"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.average").value(2.5))
                .andExpect(jsonPath("$.items", hasSize(2)));
        mvc.perform(get("/api/v1/reviews?targetType=LISTING&targetId=202"))
                .andExpect(jsonPath("$.count").value(0))
                .andExpect(jsonPath("$.average", nullValue()));
    }

    @Test
    void reviews_validation_auth_and_missing_target() throws Exception {
        mvc.perform(post("/api/v1/reviews").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(json(post("/api/v1/reviews"), """
                {"targetType":"LISTING","targetId":201,"rating":6,"scores":{"noise":3,"light":4,"bugs":5,"landlord":4,"depositReturn":5},"text":"x"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(json(post("/api/v1/reviews"), """
                {"targetType":"LISTING","targetId":201,"rating":3,"scores":{"noise":3,"light":4,"bugs":5,"landlord":4,"depositReturn":5},"text":"x","pin":[1,2]}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/reviews?targetType=LISTING&targetId=999999")).andExpect(status().isNotFound());
    }

    private static final String HOME = """
            {"address":"서울 성동구 테스트동 1","dong":"테스트동","lat":37.56,"lng":127.04,"deposit":10000000,"rent":500000,
             "mgmtFee":50000,"areaM2":18,"contractStart":"2026-03-01","contractEnd":"2027-02-28"}""";

    @Test
    void home_payments_utilities_report() throws Exception {
        mvc.perform(auth(get("/api/v1/me/home"))).andExpect(status().isNotFound());
        mvc.perform(json(put("/api/v1/me/home"), HOME)).andExpect(status().isOk())
                .andExpect(jsonPath("$.contractEnd").value("2027-02-28"));
        mvc.perform(auth(get("/api/v1/me/home"))).andExpect(jsonPath("$.rent").value(500000));

        mvc.perform(json(post("/api/v1/me/payments"), "{\"month\":\"2026-09\",\"amount\":500000,\"paidAt\":\"2026-09-01\"}")).andExpect(status().isCreated());
        mvc.perform(json(post("/api/v1/me/payments"), "{\"month\":\"2026-10\",\"amount\":480000,\"paidAt\":\"2026-10-01\"}")).andExpect(status().isCreated());
        mvc.perform(json(post("/api/v1/me/payments"), "{\"month\":\"2026-10\",\"amount\":500000,\"paidAt\":\"2026-10-02\"}")).andExpect(status().isCreated());
        mvc.perform(auth(get("/api/v1/me/payments"))).andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].month").value("2026-10")).andExpect(jsonPath("$[0].amount").value(500000));

        mvc.perform(json(post("/api/v1/me/utilities"), "{\"month\":\"2026-09\",\"electricity\":30000,\"gas\":17000,\"water\":null,\"internet\":20000}")).andExpect(status().isCreated());
        mvc.perform(json(post("/api/v1/me/utilities"), "{\"month\":\"2026-10\",\"electricity\":32000,\"gas\":38000,\"water\":12000,\"internet\":20000}")).andExpect(status().isCreated());
        mvc.perform(auth(get("/api/v1/me/utilities"))).andExpect(jsonPath("$", hasSize(2)));

        // 10월: 500000 + 50000 + 32000 + 38000 + 12000 + 20000 = 652000, 9월: 500000+50000+30000+17000+20000 = 617000
        mvc.perform(auth(get("/api/v1/me/report?month=2026-10"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(652000))
                .andExpect(jsonPath("$.prevTotal").value(617000))
                .andExpect(jsonPath("$.diff").value(35000))
                .andExpect(jsonPath("$.topIncrease.item").value("gas"))
                .andExpect(jsonPath("$.topIncrease.diff").value(21000))
                .andExpect(jsonPath("$.excess", nullValue())); // 테스트동은 표본이 없어 판정 보류
        // 지난달 기록 없음 + 납부 기록 없으면 계약 월세
        mvc.perform(auth(get("/api/v1/me/report?month=2026-12")))
                .andExpect(jsonPath("$.total").value(550000))
                .andExpect(jsonPath("$.prevTotal", nullValue()))
                .andExpect(jsonPath("$.topIncrease", nullValue()));
        mvc.perform(auth(get("/api/v1/me/report?month=2026-13"))).andExpect(status().isBadRequest());
    }

    @Test
    void alerts_crud_and_notifications() throws Exception {
        mvc.perform(json(put("/api/v1/me/home"), HOME.replace("2027-02-28", "2026-11-17"))).andExpect(status().isOk());
        String body = mvc.perform(json(post("/api/v1/me/home/alerts"), "{\"daysBefore\":10,\"enabled\":true}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long alertId = Long.parseLong(body.replaceAll(".*\"id\":(\\d+).*", "$1"));
        mvc.perform(json(post("/api/v1/me/home/alerts"), "{\"daysBefore\":400}")).andExpect(status().isBadRequest());
        mvc.perform(json(post("/api/v1/me/home/alerts"), "{\"daysBefore\":30}")).andExpect(status().isCreated());
        mvc.perform(auth(get("/api/v1/me/home/alerts"))).andExpect(jsonPath("$", hasSize(2)));

        // 단지 구독 + 내일 접수 마감 일정 → D-1 알림. 오늘은 2026-11-07(KST), 만료 2026-11-17 은 10일 전 알림과 일치
        long housing = jdbc.queryForObject("insert into housing (name, address, lat, lng, updated_at) values ('청아진', '서울', 37.565, 127.035, ?) returning id",
                Long.class, Timestamp.from(Instant.parse("2026-11-07T00:00:00Z")));
        long ann = jdbc.queryForObject("insert into announcements (housing_id, title, additional, posted_on) values (?, '공고', false, '2026-11-01') returning id", Long.class, housing);
        jdbc.update("insert into announcement_events (announcement_id, type, event_date) values (?, 'APPLY_END', ?)", ann, Date.valueOf(LocalDate.of(2026, 11, 8)));
        jdbc.update("insert into housing_subscriptions (user_id, housing_id, created_at) values (?, ?, now())", userId, housing);

        assertThat(job.run()).isEqualTo(2);
        assertThat(job.run()).isZero(); // 같은 날 다시 돌려도 중복 없음

        String list = mvc.perform(auth(get("/api/v1/me/notifications"))).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].title", containsInAnyOrder("청아진 접수 마감 D-1", "계약 만료 10일 전")))
                .andReturn().getResponse().getContentAsString();
        long nid = Long.parseLong(list.replaceAll("^\\[\\{\"id\":(\\d+).*", "$1"));
        mvc.perform(auth(patch("/api/v1/me/notifications/" + nid + "/read"))).andExpect(status().isNoContent());
        mvc.perform(auth(patch("/api/v1/me/notifications/999999/read"))).andExpect(status().isNotFound());

        // 끈 알림은 만들지 않음, 삭제
        mvc.perform(json(patch("/api/v1/me/home/alerts/" + alertId), "{\"enabled\":false}")).andExpect(jsonPath("$.enabled").value(false));
        mvc.perform(auth(delete("/api/v1/me/home/alerts/" + alertId))).andExpect(status().isNoContent());
        mvc.perform(auth(get("/api/v1/me/home/alerts"))).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void search_db_first_then_address() throws Exception {
        addressPort.setResults(List.of(new AddressResult("서울 성동구 행당동 1", "서울 성동구 왕십리로 1", "행당동", 37.56, 127.03)));
        mvc.perform(get("/api/v1/search").param("q", "행당")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("LISTING"))
                .andExpect(jsonPath("$[0].id").value(201));
        mvc.perform(get("/api/v1/search").param("q", "없는이름xyz")).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].type").value("ADDRESS"))
                .andExpect(jsonPath("$[0].id", nullValue()))
                .andExpect(jsonPath("$[0].name").value("서울 성동구 왕십리로 1"));
        addressPort.setFailing(true);
        mvc.perform(get("/api/v1/search").param("q", "행당")).andExpect(status().isOk());
        mvc.perform(get("/api/v1/search").param("q", "행")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/search").param("q", "50%_")).andExpect(status().isOk());
    }
}
