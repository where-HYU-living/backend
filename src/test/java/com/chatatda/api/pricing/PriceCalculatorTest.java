package com.chatatda.api.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriceCalculatorTest {

    static final PricingProperties DEFAULTS = new PricingProperties(0.06, 0.05, 0.15, 0.30, 10, 0.30, 12, 24, 0);

    private final PriceCalculator calc = new PriceCalculator(DEFAULTS);

    private static Sample dongSample(int count, Double medianPerM2) {
        return new Sample(count, "DONG", List.of("행당동"), 12.6, 23.4, YearMonth.of(2025, 11), YearMonth.of(2026, 10), medianPerM2);
    }

    @Test
    void convertedRent_includesDepositAndMgmt() {
        assertThat(calc.convertedRent(10_000_000, 550_000, 80_000L, 0.06)).isEqualTo(680_000);
    }

    @Test
    void convertedRent_nullMgmt_treatedAsZero() {
        assertThat(calc.convertedRent(0, 400_000, null, 0.06)).isEqualTo(400_000);
    }

    @Test
    void convertedRent_zeroMgmtAndZeroDeposit() {
        assertThat(calc.convertedRent(0, 400_000, 0L, 0.06)).isEqualTo(400_000);
        assertThat(calc.convertedRent(0, 400_000, 70_000L, 0.06)).isEqualTo(470_000);
        assertThat(calc.convertedRent(5_000_000, 400_000, 0L, 0.06)).isEqualTo(425_000);
    }

    @Test
    void evaluate_computesExcess() {
        var s = dongSample(23, 30_000.0);
        var r = calc.evaluate(new PriceInput(10_000_000, 550_000, 80_000L, 18.0, "행당동", 24), s);

        assertThat(r.convertedRent()).isEqualTo(680_000);
        assertThat(r.fairRent()).isEqualTo(540_000);
        assertThat(r.monthlyExcess()).isEqualTo(140_000);
        assertThat(r.contractMonths()).isEqualTo(24);
        assertThat(r.totalExcess()).isEqualTo(3_360_000);
        assertThat(r.excessRate()).isEqualTo(0.259);
        assertThat(r.grade()).isEqualTo(Grade.HIGH);
        assertThat(r.conversionRate()).isEqualTo(0.06);
        assertThat(r.sample()).isEqualTo(s);
    }

    @Test
    void evaluate_pendingWhenNoSample() {
        var input = new PriceInput(10_000_000, 550_000, 80_000L, 18.0, "행당동", 24);
        var r = calc.evaluate(input, new Sample(4, "NONE", List.of(), null, null, null, null, null));

        assertThat(r.grade()).isEqualTo(Grade.PENDING);
        assertThat(r.convertedRent()).isEqualTo(680_000);
        assertThat(r.fairRent()).isNull();
        assertThat(r.monthlyExcess()).isNull();
        assertThat(r.totalExcess()).isNull();
        assertThat(r.excessRate()).isNull();
    }

    @Test
    void evaluate_pendingWhenBelowMinSample_evenWithMedian() {
        var r = calc.evaluate(new PriceInput(0, 500_000, null, 18.0, "행당동", 24), dongSample(9, 30_000.0));
        assertThat(r.grade()).isEqualTo(Grade.PENDING);
        assertThat(r.fairRent()).isNull();
    }

    @Test
    void evaluate_cheaperThanMarket_keepsNegativeExcess() {
        var r = calc.evaluate(new PriceInput(0, 400_000, 0L, 18.0, "행당동", 24), dongSample(23, 30_000.0));

        assertThat(r.monthlyExcess()).isEqualTo(-140_000);
        assertThat(r.totalExcess()).isEqualTo(-3_360_000);
        assertThat(r.excessRate()).isEqualTo(-0.259);
        assertThat(r.grade()).isEqualTo(Grade.FAIR);
    }

    @Test
    void evaluate_gradeUsesRoundedRate() {
        var r = calc.evaluate(new PriceInput(0, 567_216, 0L, 18.0, "행당동", 24), dongSample(23, 30_000.0));

        assertThat(r.excessRate()).isEqualTo(0.050);
        assertThat(r.grade()).isEqualTo(Grade.FAIR);
    }

    @Test
    void evaluate_contractMonthsFromInput() {
        var r = calc.evaluate(new PriceInput(10_000_000, 550_000, 80_000L, 18.0, "행당동", 8), dongSample(23, 30_000.0));
        assertThat(r.contractMonths()).isEqualTo(8);
        assertThat(r.totalExcess()).isEqualTo(1_120_000);
    }

    @Test
    void evaluate_baselineMgmtFeeAddedToFairRent() {
        var withBaseline = new PriceCalculator(new PricingProperties(0.06, 0.05, 0.15, 0.30, 10, 0.30, 12, 24, 50_000));
        var r = withBaseline.evaluate(new PriceInput(10_000_000, 550_000, 80_000L, 18.0, "행당동", 24), dongSample(23, 30_000.0));

        assertThat(r.fairRent()).isEqualTo(590_000);
        assertThat(r.monthlyExcess()).isEqualTo(90_000);
    }

    @Test
    void properties_rejectInvalidValues() {
        assertThatThrownBy(() -> new PricingProperties(0, 0.05, 0.15, 0.30, 10, 0.30, 12, 24, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PricingProperties(0.06, 0.15, 0.05, 0.30, 10, 0.30, 12, 24, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PricingProperties(0.06, 0.05, 0.15, 0.30, 0, 0.30, 12, 24, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PricingProperties(0.06, 0.05, 0.15, 0.30, 10, 0.30, 12, 24, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
