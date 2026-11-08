package com.chatatda.api.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatatda.api.ai.AiServerClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class AiRentServiceTest {

    private static final PricingProperties PROPS = new PricingProperties(0.046, 0.10, 0.25, 0.50, 10, 0.30, 12, 24, 71_000);

    private static AiRentService service(boolean enabled, String location) {
        return new AiRentService(new AiServerClient(enabled, "http://127.0.0.1:9"), PROPS, Clock.systemUTC(),
                new ObjectMapper(), new DefaultResourceLoader(), location);
    }

    @Test
    void aiOff_usesFixtures() {
        AiRentService s = service(false, "classpath:test-fixtures/ai-rent.json");

        assertThat(s.forListing(1, "사근동", 11, 2, 1998)).isEqualTo(new AiEstimate(490_000, 450_000, 550_000));
        assertThat(s.forListing(2, "사근동", 18, null, null)).isEqualTo(new AiEstimate(520_000, 520_000, 520_000));
    }

    @Test
    void emptyOrUnknownListing_isNull() {
        AiRentService s = service(false, "classpath:test-fixtures/ai-rent.json");

        assertThat(s.forListing(3, "사근동", 11, null, null)).isNull();
        assertThat(s.forListing(99, "사근동", 11, null, null)).isNull();
    }

    @Test
    void missingFile_isEmptyNotError() {
        assertThat(service(false, "classpath:none.json").forListing(1, "사근동", 11, null, null)).isNull();
    }

    @Test
    void realFixtures_allTwentyListingsFilledAndOrdered() {
        AiRentService s = service(false, "classpath:fixtures/ai-rent.json");

        for (long id = 1; id <= 20; id++) {
            AiEstimate e = s.forListing(id, "사근동", 20, null, null);
            assertThat(e).as("매물 %d", id).isNotNull();
            assertThat(e.low()).isPositive().isLessThanOrEqualTo(e.fairRent());
            assertThat(e.fairRent()).isLessThanOrEqualTo(e.high());
        }
    }

    @Test
    void aiOnButUnreachable_fallsBackToFixtures() {
        AiRentService s = service(true, "classpath:test-fixtures/ai-rent.json");

        assertThat(s.forListing(1, "사근동", 11, 2, 1998)).isEqualTo(new AiEstimate(490_000, 450_000, 550_000));
    }
}
