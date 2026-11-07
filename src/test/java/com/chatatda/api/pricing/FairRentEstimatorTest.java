package com.chatatda.api.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatatda.api.realdeal.RealDeal;
import com.chatatda.api.realdeal.RealDealRepository;
import com.chatatda.api.realdeal.RealDealType;
import com.chatatda.api.support.IntegrationTest;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class FairRentEstimatorTest extends IntegrationTest {

    private static final YearMonth AS_OF = YearMonth.of(2026, 10);

    @Autowired
    FairRentEstimator estimator;

    @Autowired
    RealDealRepository repository;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void clean() {
        jdbc.execute("truncate table real_deals restart identity");
    }

    private void deal(String dong, double area, long perM2, YearMonth ym, String contractType, long deposit) {
        long rent = Math.round(perM2 * area);
        repository.save(new RealDeal("11200", RealDealType.DETACHED, dong, area, deposit, rent, ym, 2, 2010,
                contractType, clock.instant()));
    }

    private void newDeals(String dong, int count, long startPerM2) {
        for (int i = 0; i < count; i++) {
            deal(dong, 18.0, startPerM2 + 1_000L * i, AS_OF, "신규", 0);
        }
    }

    @Test
    void estimator_usesSameDong_whenEnough() {
        newDeals("행당동", 12, 20_000);

        Sample s = estimator.sampleFor("행당동", 18.0, AS_OF);

        assertThat(s.scope()).isEqualTo("DONG");
        assertThat(s.count()).isEqualTo(12);
        assertThat(s.dongs()).containsExactly("행당동");
        assertThat(s.medianPerM2()).isEqualTo(25_500.0);
        assertThat(s.areaMinM2()).isEqualTo(12.6);
        assertThat(s.areaMaxM2()).isEqualTo(23.4);
        assertThat(s.from()).isEqualTo(YearMonth.of(2025, 11));
        assertThat(s.to()).isEqualTo(AS_OF);
    }

    @Test
    void estimator_expandsToAdjacent() {
        newDeals("행당동", 4, 20_000);
        newDeals("사근동", 8, 30_000);

        Sample s = estimator.sampleFor("행당동", 18.0, AS_OF);

        assertThat(s.scope()).isEqualTo("ADJACENT");
        assertThat(s.count()).isEqualTo(12);
        assertThat(s.dongs()).containsExactly("행당동", "사근동");
        assertThat(s.medianPerM2()).isNotNull();
    }

    @Test
    void estimator_noneWhenStillShort() {
        newDeals("행당동", 4, 20_000);
        newDeals("사근동", 5, 30_000);

        Sample s = estimator.sampleFor("행당동", 18.0, AS_OF);

        assertThat(s.scope()).isEqualTo("NONE");
        assertThat(s.count()).isEqualTo(9);
        assertThat(s.medianPerM2()).isNull();
        assertThat(s.dongs()).isEmpty();
    }

    @Test
    void estimator_ignoresOldDeals() {
        newDeals("행당동", 10, 20_000);
        deal("행당동", 18.0, 99_000, AS_OF.minusMonths(12), "신규", 0);

        Sample s = estimator.sampleFor("행당동", 18.0, AS_OF);

        assertThat(s.scope()).isEqualTo("DONG");
        assertThat(s.count()).isEqualTo(10);
    }

    @Test
    void estimator_usesOnlyNewMonthlyContractsWithinArea() {
        newDeals("행당동", 10, 20_000);
        deal("행당동", 18.0, 99_000, AS_OF, "갱신", 0);
        deal("행당동", 18.0, 99_000, AS_OF, null, 0);
        deal("행당동", 18.0, 0, AS_OF, "신규", 200_000_000);
        deal("행당동", 30.0, 99_000, AS_OF, "신규", 0);
        deal("행당동", 12.0, 99_000, AS_OF, "신규", 0);

        Sample s = estimator.sampleFor("행당동", 18.0, AS_OF);

        assertThat(s.count()).isEqualTo(10);
        assertThat(s.medianPerM2()).isEqualTo(24_500.0);
    }

    @Test
    void estimator_includesDepositConversionInPerM2() {
        for (int i = 0; i < 10; i++) {
            deal("행당동", 20.0, 20_000, AS_OF, "신규", 10_000_000);
        }

        Sample s = estimator.sampleFor("행당동", 20.0, AS_OF);

        assertThat(s.medianPerM2()).isEqualTo(22_500.0);
    }
}
