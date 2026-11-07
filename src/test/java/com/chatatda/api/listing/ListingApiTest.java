package com.chatatda.api.listing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatatda.api.realdeal.RealDeal;
import com.chatatda.api.realdeal.RealDealRepository;
import com.chatatda.api.realdeal.RealDealType;
import com.chatatda.api.support.IntegrationTest;
import com.chatatda.api.user.Budget;
import com.chatatda.api.user.User;
import com.chatatda.api.user.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class ListingApiTest extends IntegrationTest {

    @Autowired
    RealDealRepository deals;

    @Autowired
    ListingService listingService;

    @Autowired
    UserRepository users;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @BeforeEach
    void dealsForHaengdang() {
        jdbc.execute("truncate table real_deals restart identity");
        for (int i = 0; i < 12; i++) {
            deals.save(new RealDeal("11200", RealDealType.DETACHED, "행당동", 18.0, 0, 540_000, YearMonth.of(2026, 10),
                    2, 2010, "신규", clock.instant()));
        }
        listingService.recomputeAll();
    }

    @Test
    void list_bboxFilter() throws Exception {
        mvc.perform(get("/api/v1/listings").param("bbox", "127.039,37.557,127.043,37.559"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.items[*].lng", everyItem(lessThanOrEqualTo(127.043))))
                .andExpect(jsonPath("$.items[*].lat", everyItem(lessThanOrEqualTo(37.559))));
    }

    @Test
    void list_invalidBbox_400() throws Exception {
        for (String bbox : new String[] {"1,2,3", "a,b,c,d", "127.05,37.55,127.04,37.56"}) {
            mvc.perform(get("/api/v1/listings").param("bbox", bbox))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }

    @Test
    void list_gradeFilter() throws Exception {
        mvc.perform(get("/api/v1/listings").param("grade", "HIGH,CULPRIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", greaterThan(0)))
                .andExpect(jsonPath("$.items[*].grade", everyItem(in(List.of("HIGH", "CULPRIT")))));
    }

    @Test
    void list_maxRentFilter() throws Exception {
        mvc.perform(get("/api/v1/listings").param("maxRent", "500000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[*].rent", everyItem(lessThanOrEqualTo(500000))));
    }

    @Test
    void list_walkFilterIgnoredWithoutBuilding() throws Exception {
        mvc.perform(get("/api/v1/listings").param("maxWalkMin", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.items[*].walkMin", everyItem(nullValue())));
    }

    @Test
    void list_pagination() throws Exception {
        mvc.perform(get("/api/v1/listings").param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[0].id").value(203))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(2))
                .andExpect(jsonPath("$.total").value(6));
    }

    @Test
    void list_sizeCappedAt100() throws Exception {
        mvc.perform(get("/api/v1/listings").param("size", "1000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void list_summaryShape_ignoresComputedFieldsFromFixture() throws Exception {
        mvc.perform(get("/api/v1/listings").param("size", "1"))
                .andExpect(jsonPath("$.items[0].id").value(201))
                .andExpect(jsonPath("$.items[0].convertedRent").value(680000))
                .andExpect(jsonPath("$.items[0].favorited").value(false))
                .andExpect(jsonPath("$.items[0].walkMin").isEmpty())
                .andExpect(jsonPath("$.items[0].roomType").value("SEPARATED"))
                .andExpect(jsonPath("$.items[0].thumbnailUrl").value("https://example.test/201.jpg"))
                .andExpect(jsonPath("$.items[0].options").doesNotExist());
    }

    @Test
    void detail_ok() throws Exception {
        mvc.perform(get("/api/v1/listings/201"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.options[0]").value("에어컨"))
                .andExpect(jsonPath("$.photos", hasSize(2)))
                .andExpect(jsonPath("$.floorplanId").value(5))
                .andExpect(jsonPath("$.builtYear").value(2019))
                .andExpect(jsonPath("$.description").value("테스트 매물"))
                .andExpect(jsonPath("$.mgmtFee").value(80000));
    }

    @Test
    void detail_404() throws Exception {
        mvc.perform(get("/api/v1/listings/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void priceCheck_shape() throws Exception {
        mvc.perform(get("/api/v1/listings/201/price-check"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.convertedRent").value(680000))
                .andExpect(jsonPath("$.fairRent").value(611000))
                .andExpect(jsonPath("$.contractMonths").value(24))
                .andExpect(jsonPath("$.grade").isString())
                .andExpect(jsonPath("$.conversionRate").isNumber())
                .andExpect(jsonPath("$.sample.scope").value("DONG"))
                .andExpect(jsonPath("$.sample.count").value(12))
                .andExpect(jsonPath("$.sample.dongs[0]").value("행당동"))
                .andExpect(jsonPath("$.sample.from").value("2025-11"))
                .andExpect(jsonPath("$.sample.to").value("2026-10"))
                .andExpect(jsonPath("$.sample.medianPerM2").doesNotExist())
                .andExpect(jsonPath("$.budget").isEmpty());
    }

    @Test
    void priceCheck_matchesCachedGrade() throws Exception {
        JsonNode check = json(mvc.perform(get("/api/v1/listings/202/price-check")).andReturn().getResponse().getContentAsString());
        JsonNode summary = json(mvc.perform(get("/api/v1/listings/202")).andReturn().getResponse().getContentAsString());

        assertThat(check.get("grade").asText()).isEqualTo("CULPRIT").isEqualTo(summary.get("grade").asText());
    }

    @Test
    void priceCheck_pendingListing_returns200WithNulls() throws Exception {
        for (String id : new String[] {"204", "206"}) {
            mvc.perform(get("/api/v1/listings/" + id + "/price-check"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.grade").value("PENDING"))
                    .andExpect(jsonPath("$.convertedRent").isNumber())
                    .andExpect(jsonPath("$.fairRent").isEmpty())
                    .andExpect(jsonPath("$.monthlyExcess").isEmpty())
                    .andExpect(jsonPath("$.totalExcess").isEmpty())
                    .andExpect(jsonPath("$.excessRate").isEmpty())
                    .andExpect(jsonPath("$.sample.scope").value("NONE"));
        }
    }

    @Test
    void priceCheck_adjacentScope() throws Exception {
        mvc.perform(get("/api/v1/listings/205/price-check"))
                .andExpect(jsonPath("$.sample.scope").value("ADJACENT"))
                .andExpect(jsonPath("$.sample.dongs[0]").value("행당동"));
    }

    @Test
    void priceCheck_budgetForLoggedIn() throws Exception {
        User user = new User("budget@hanyang.ac.kr", "한양0100", clock.instant());
        user.changeBudget(new Budget(600_000L, 5_000_000L));
        long userId = users.save(user).getId();

        mvc.perform(get("/api/v1/listings/201/price-check").header("Authorization", bearer(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budget.rentCap").value(600000))
                .andExpect(jsonPath("$.budget.depositCap").value(5000000))
                .andExpect(jsonPath("$.budget.overRent").value(80000))
                .andExpect(jsonPath("$.budget.overDeposit").value(5000000));
    }

    @Test
    void priceCheck_budgetWithOnlyRentCap() throws Exception {
        User user = new User("rentonly@hanyang.ac.kr", "한양0101", clock.instant());
        user.changeBudget(new Budget(800_000L, null));
        long userId = users.save(user).getId();

        mvc.perform(get("/api/v1/listings/201/price-check").header("Authorization", bearer(userId)))
                .andExpect(jsonPath("$.budget.overRent").value(0))
                .andExpect(jsonPath("$.budget.overDeposit").isEmpty());
    }

    private JsonNode json(String body) throws Exception {
        return objectMapper.readTree(body);
    }
}
