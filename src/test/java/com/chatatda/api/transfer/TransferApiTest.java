package com.chatatda.api.transfer;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatatda.api.realdeal.RealDeal;
import com.chatatda.api.realdeal.RealDealRepository;
import com.chatatda.api.realdeal.RealDealType;
import com.chatatda.api.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.YearMonth;
import java.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;

class TransferApiTest extends IntegrationTest {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 0};
    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0};

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RealDealRepository deals;

    @Autowired
    ObjectMapper objectMapper;

    long author;
    long other;

    @BeforeEach
    void data() {
        jdbc.execute("truncate table real_deals restart identity");
        for (int i = 0; i < 12; i++) {
            deals.save(new RealDeal("11200", RealDealType.DETACHED, "행당동", 18.0, 0, 540_000, YearMonth.of(2026, 10),
                    2, 2010, "신규", clock.instant()));
        }
        author = user("a");
        other = user("b");
    }

    private long user(String nick) {
        return jdbc.queryForObject("insert into users (email, nickname, created_at) values (?, ?, now()) returning id",
                Long.class, nick + "@hanyang.ac.kr", nick);
    }

    private String body(String title, String dong, long rent, int months, String photos) {
        return """
                {"title": %s, "address": "서울 성동구 %s 1", "dong": "%s", "lat": 37.558, "lng": 127.041,
                 "deposit": 0, "rent": %d, "mgmtFee": 0, "areaM2": 18, "roomType": "OPEN", "floor": 3,
                 "remainingMonths": %d, "availableFrom": "2026-12-01", "description": "학기 끝나고 나가요",
                 "photoUrls": %s}""".formatted(title == null ? "null" : "\"" + title + "\"", dong, dong, rent, months, photos);
    }

    private ResultActions create(long userId, String json) throws Exception {
        return mvc.perform(post("/api/v1/transfers").header("Authorization", bearer(userId))
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long createdId(long userId, String json) throws Exception {
        String res = create(userId, json).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).get("id").asLong();
    }

    private String upload(long userId, byte[] bytes, String name, String type) throws Exception {
        String res = mvc.perform(multipart("/api/v1/uploads").file(new MockMultipartFile("file", name, type, bytes))
                        .header("Authorization", bearer(userId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(res).get("url").asText();
    }

    @Test
    void create_computesPriceCheck() throws Exception {
        create(author, body("행당동 원룸 양도해요", "행당동", 700_000, 8, "[]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("행당동 원룸 양도해요"))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.mine").value(true))
                .andExpect(jsonPath("$.authorNickname").value("a"))
                .andExpect(jsonPath("$.remainingMonths").value(8))
                .andExpect(jsonPath("$.availableFrom").value("2026-12-01"))
                .andExpect(jsonPath("$.convertedRent").value(700_000))
                .andExpect(jsonPath("$.grade").value("SLIGHTLY_HIGH"))
                .andExpect(jsonPath("$.priceCheck.contractMonths").value(8))
                .andExpect(jsonPath("$.priceCheck.grade").value("SLIGHTLY_HIGH"))
                .andExpect(jsonPath("$.priceCheck.totalExcess").value(89_000 * 8));
    }

    @Test
    void create_validation() throws Exception {
        create(author, body("", "행당동", -1, 0, "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details.rent").exists())
                .andExpect(jsonPath("$.details.remainingMonths").exists())
                .andExpect(jsonPath("$.details.title").exists());
        create(author, body(null, "행당동", 500_000, 6, "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.title").exists());
        create(author, body("a".repeat(41), "행당동", 500_000, 6, "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.title").exists());
        create(author, body("  a  ", "행당동", 500_000, 6, "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.title").exists());
        create(author, body("a", "행당동", 500_000, 6, "[]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.title").exists());
    }

    @Test
    void create_guest401() throws Exception {
        mvc.perform(post("/api/v1/transfers").contentType(MediaType.APPLICATION_JSON)
                .content(body("t", "행당동", 500_000, 6, "[]"))).andExpect(status().isUnauthorized());
    }

    @Test
    void pendingWhenFewDeals() throws Exception {
        create(author, body("테스트동 양도", "테스트동", 500_000, 6, "[]"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.grade").value("PENDING"))
                .andExpect(jsonPath("$.priceCheck.grade").value("PENDING"));
    }

    @Test
    void update_byAuthor_recomputes_byOther_403() throws Exception {
        long id = createdId(author, body("양도", "행당동", 650_000, 8, "[]"));

        mvc.perform(put("/api/v1/transfers/" + id).header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content(body("양도 (가격 내림)", "행당동", 600_000, 7, "[]")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("양도 (가격 내림)"))
                .andExpect(jsonPath("$.convertedRent").value(600_000))
                .andExpect(jsonPath("$.grade").value("FAIR"))
                .andExpect(jsonPath("$.priceCheck.contractMonths").value(7));

        mvc.perform(put("/api/v1/transfers/" + id).header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON).content(body("뺏기", "행당동", 1, 7, "[]")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void markDone_hiddenFromDefaultList_andReopen() throws Exception {
        long id = createdId(author, body("양도", "행당동", 650_000, 8, "[]"));

        mvc.perform(patch("/api/v1/transfers/" + id + "/status").header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/v1/transfers/" + id + "/status").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"DONE\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DONE"));

        mvc.perform(get("/api/v1/transfers")).andExpect(jsonPath("$.items", hasSize(0)));
        mvc.perform(get("/api/v1/transfers").param("status", "DONE")).andExpect(jsonPath("$.items[*].id", contains((int) id)));

        mvc.perform(patch("/api/v1/transfers/" + id + "/status").header("Authorization", bearer(author))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"OPEN\"}"))
                .andExpect(jsonPath("$.status").value("OPEN"));
        mvc.perform(get("/api/v1/transfers")).andExpect(jsonPath("$.items", hasSize(1)));
    }

    @Test
    void list_summaryFields_bbox_favorited_mine() throws Exception {
        String photo = upload(author, PNG, "a.png", "image/png");
        long id = createdId(author, body("양도", "행당동", 650_000, 8, "[\"" + photo + "\"]"));
        jdbc.update("insert into favorites (user_id, type, target_id, created_at) values (?, 'TRANSFER', ?, now())", other, id);

        mvc.perform(get("/api/v1/transfers").header("Authorization", bearer(other)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value("양도"))
                .andExpect(jsonPath("$.items[0].thumbnailUrl").value(photo))
                .andExpect(jsonPath("$.items[0].favorited").value(true))
                .andExpect(jsonPath("$.items[0].authorNickname").value("a"))
                .andExpect(jsonPath("$.items[0].remainingMonths").value(8));
        mvc.perform(get("/api/v1/transfers").param("bbox", "127.0,37.0,127.01,37.01"))
                .andExpect(jsonPath("$.items", hasSize(0)));

        mvc.perform(get("/api/v1/transfers/" + id).header("Authorization", bearer(other)))
                .andExpect(jsonPath("$.mine").value(false))
                .andExpect(jsonPath("$.favorited").value(true))
                .andExpect(jsonPath("$.photos", contains(photo)))
                .andExpect(jsonPath("$.description").value("학기 끝나고 나가요"));
        mvc.perform(get("/api/v1/transfers/" + id))
                .andExpect(jsonPath("$.mine").value(false))
                .andExpect(jsonPath("$.favorited").value(false));
    }

    @Test
    void detail_unknown404() throws Exception {
        mvc.perform(get("/api/v1/transfers/999999")).andExpect(status().isNotFound());
    }

    @Test
    void favorite_transfer() throws Exception {
        long id = createdId(author, body("행당동 원룸 양도해요", "행당동", 700_000, 8, "[]"));

        mvc.perform(post("/api/v1/me/favorites").header("Authorization", bearer(other))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"TRANSFER\",\"targetId\":" + id + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("행당동 원룸 양도해요"))
                .andExpect(jsonPath("$.grade").value("SLIGHTLY_HIGH"))
                .andExpect(jsonPath("$.convertedRent").value(700_000));
    }

    @Test
    void upload_png_jpg_servedBack() throws Exception {
        String png = upload(author, PNG, "room.png", "image/png");
        String jpg = upload(author, JPG, "room.jpg", "image/jpeg");

        org.assertj.core.api.Assertions.assertThat(png).startsWith("http://localhost/files/").endsWith(".png");
        org.assertj.core.api.Assertions.assertThat(jpg).endsWith(".jpg");
        mvc.perform(get(png.substring("http://localhost".length())))
                .andExpect(status().isOk())
                .andExpect(content().bytes(PNG));
    }

    @Test
    void upload_rejectsNonImage() throws Exception {
        mvc.perform(multipart("/api/v1/uploads")
                        .file(new MockMultipartFile("file", "fake.png", "image/png", "hello".getBytes()))
                        .header("Authorization", bearer(author)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void upload_rejectsOver5MB() throws Exception {
        byte[] big = Arrays.copyOf(PNG, 6 * 1024 * 1024);
        mvc.perform(multipart("/api/v1/uploads").file(new MockMultipartFile("file", "big.png", "image/png", big))
                        .header("Authorization", bearer(author)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_guest401() throws Exception {
        mvc.perform(multipart("/api/v1/uploads").file(new MockMultipartFile("file", "a.png", "image/png", PNG)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void photoUrls_onlyOurUploads() throws Exception {
        create(author, body("양도", "행당동", 650_000, 8, "[\"https://evil.example.com/x.png\"]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.photoUrls").exists());
    }

    @Test
    void list_newestFirst_excludesNothingElse() throws Exception {
        long first = createdId(author, body("첫 글", "행당동", 650_000, 8, "[]"));
        clock.advance(java.time.Duration.ofMinutes(1));
        long second = createdId(other, body("둘째 글", "행당동", 600_000, 5, "[]"));

        JsonNode page = objectMapper.readTree(mvc.perform(get("/api/v1/transfers"))
                .andReturn().getResponse().getContentAsString());
        org.assertj.core.api.Assertions.assertThat(page.get("items").get(0).get("id").asLong()).isEqualTo(second);
        org.assertj.core.api.Assertions.assertThat(page.get("items").get(1).get("id").asLong()).isEqualTo(first);
        mvc.perform(get("/api/v1/transfers").header("Authorization", bearer(other)))
                .andExpect(jsonPath("$.items[0].favorited").value(false))
                .andExpect(jsonPath("$.items[*].status", not(contains("DONE"))))
                .andExpect(jsonPath("$.items[0].grade").value(startsWith("F")));
    }
}
