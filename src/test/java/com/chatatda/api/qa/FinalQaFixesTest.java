package com.chatatda.api.qa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatatda.api.auth.AccountService;
import com.chatatda.api.auth.EmailCodeService;
import com.chatatda.api.support.IntegrationTest;
import com.chatatda.api.user.ProfileService;
import com.chatatda.api.world.InviteService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** 최종 QA(2026-11)에서 고친 것: 동시 요청·입력 경계·도보 계산 보호 */
class FinalQaFixesTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AccountService accounts;

    @Autowired
    ProfileService profiles;

    @Autowired
    EmailCodeService emailCodes;

    @Autowired
    InviteService invites;

    private long newUser(String email) {
        return jdbc.queryForObject("insert into users (email, nickname, created_at) values (?, ?, now()) returning id",
                Long.class, email, "qa" + Math.abs(email.hashCode() % 100000));
    }

    /** 같은 일을 n 개 스레드에서 한꺼번에 시작한다 */
    private <T> List<T> together(int n, Callable<T> work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(n);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<T>> fs = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            fs.add(pool.submit(() -> {
                go.await();
                return work.call();
            }));
        }
        go.countDown();
        List<T> out = new ArrayList<>();
        for (Future<T> f : fs) {
            out.add(f.get());
        }
        pool.shutdown();
        return out;
    }

    @Test
    void dormList_hasWalkMin_whenBuildingGiven() throws Exception {
        mvc.perform(get("/api/v1/dorms").param("buildingId", "101"))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].walkMin").value(7));
        mvc.perform(get("/api/v1/dorms")).andExpect(jsonPath("$[0].walkMin").value(nullValue()));
    }

    @Test
    void badCoordinates_areRejected_withoutTrippingTmapForEveryone() throws Exception {
        for (int i = 0; i < 4; i++) {
            mvc.perform(get("/api/v1/walk").param("fromLat", "999").param("fromLng", "-999").param("buildingId", "101"))
                    .andExpect(status().isBadRequest());
        }
        assertThat(walkPort.calls()).isZero();
        mvc.perform(get("/api/v1/walk").param("fromLat", "37.558").param("fromLng", "127.041").param("buildingId", "101"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.minutes").value(7));
    }

    @Test
    void sameWalkKey_atOnce_callsTmapOnce() throws Exception {
        List<Integer> mins = together(8, () -> {
            var r = mvc.perform(get("/api/v1/walk").param("fromLat", "37.5581").param("fromLng", "127.0412").param("buildingId", "101"))
                    .andReturn().getResponse();
            return r.getStatus();
        });
        assertThat(mins).containsOnly(200);
        assertThat(walkPort.calls()).isBetween(1, 2); // 거의 동시에 오면 한 번 (아주 조금 늦게 온 것은 캐시)
    }

    @Test
    void hugePage_is400_not500() throws Exception {
        mvc.perform(get("/api/v1/listings").param("page", "200000000")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/transfers").param("page", "200000000")).andExpect(status().isBadRequest());
    }

    @Test
    void uploadWithoutMultipart_is400() throws Exception {
        long u = newUser("up@hanyang.ac.kr");
        mvc.perform(post("/api/v1/uploads").header("Authorization", bearer(u)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void floatRating_is400() throws Exception {
        long u = newUser("rv@hanyang.ac.kr");
        mvc.perform(post("/api/v1/reviews").header("Authorization", bearer(u)).contentType(MediaType.APPLICATION_JSON).content("""
                {"targetType":"LISTING","targetId":201,"rating":4.7,"scores":{"noise":3,"light":3,"bugs":3,"landlord":3,"depositReturn":3},"text":"좋아요"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    void sameAlertTwice_staysOne() throws Exception {
        long u = newUser("al@hanyang.ac.kr");
        List<Integer> codes = together(6, () -> mvc.perform(post("/api/v1/me/home/alerts").header("Authorization", bearer(u))
                .contentType(MediaType.APPLICATION_JSON).content("{\"daysBefore\":60,\"enabled\":true}")).andReturn().getResponse().getStatus());
        assertThat(codes).containsOnly(201);
        mvc.perform(get("/api/v1/me/home/alerts").header("Authorization", bearer(u))).andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void firstLogin_doubleSubmit_makesOneAccount() throws Exception {
        List<Long> ids = together(6, () -> accounts.loginVerifiedEmail("twice@hanyang.ac.kr").user().getId());
        assertThat(new HashSet<>(ids)).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from users where email = 'twice@hanyang.ac.kr'", Integer.class)).isEqualTo(1);
    }

    @Test
    void profileSaves_atOnce_allSucceed() throws Exception {
        long u = newUser("pf@hanyang.ac.kr");
        List<Integer> sizes = together(8, () -> profiles.update(u, new ProfileService.ProfileUpdate(null, List.of(101L, 102L), null)).buildingIds().size());
        assertThat(sizes).containsOnly(2);
    }

    @Test
    void emailSend_atOnce_mailsOnce() throws Exception {
        List<String> results = together(6, () -> {
            try {
                emailCodes.send("burst@hanyang.ac.kr");
                return "sent";
            } catch (RuntimeException e) {
                return "refused";
            }
        });
        assertThat(results.stream().filter("sent"::equals).count()).isEqualTo(1);
        assertThat(mail.sentCount()).isEqualTo(1);
    }

    @Test
    void wrongCodes_atOnce_countEveryTry() throws Exception {
        emailCodes.send("guess@hanyang.ac.kr");
        together(12, () -> {
            try {
                emailCodes.verify("guess@hanyang.ac.kr", "000000".equals(mail.lastCode("guess@hanyang.ac.kr")) ? "111111" : "000000");
            } catch (RuntimeException ignored) {
                // 틀림·너무 많이 틀림
            }
            return 0;
        });
        Integer attempts = jdbc.queryForObject("select attempts from email_codes where email = 'guess@hanyang.ac.kr'", Integer.class);
        assertThat(attempts).isEqualTo(5); // 동시에 12번 쳐도 5번까지만 세고 잠긴다 (예전엔 더 많이 평가됐다)
    }

    @Test
    void inviteToken_isRevoked_whenHostLeaves() {
        String token = invites.issue("host1234");
        assertThat(invites.resolve(token)).contains("host1234");
        invites.revoke("host1234");
        assertThat(invites.resolve(token)).isEmpty();
    }

    @Test
    void transferCoordinates_outsideKorea_are400() throws Exception {
        long u = newUser("tr@hanyang.ac.kr");
        mvc.perform(post("/api/v1/transfers").header("Authorization", bearer(u)).contentType(MediaType.APPLICATION_JSON).content("""
                {"title":"좌표 이상한 글","address":"서울","dong":"행당동","lat":999,"lng":-999,"deposit":1000000,"rent":500000,
                 "mgmtFee":null,"areaM2":18,"roomType":"OPEN","floor":2,"remainingMonths":6,"availableFrom":null,"description":"","photoUrls":[]}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/v1/me/home").header("Authorization", bearer(u)).contentType(MediaType.APPLICATION_JSON).content("""
                {"address":"x","dong":"행당동","lat":0,"lng":0,"deposit":1,"rent":1,"mgmtFee":null,"areaM2":18,"contractStart":"2026-03-01","contractEnd":"2027-02-28"}"""))
                .andExpect(status().isBadRequest());
    }
}
