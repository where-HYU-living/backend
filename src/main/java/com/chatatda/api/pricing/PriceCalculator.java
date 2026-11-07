package com.chatatda.api.pricing;

import java.math.BigDecimal;
import java.math.RoundingMode;

public class PriceCalculator {

    private static final BigDecimal MONTHS_PER_YEAR = BigDecimal.valueOf(12);

    private final PricingProperties properties;
    private final GradePolicy gradePolicy;

    public PriceCalculator(PricingProperties properties) {
        this.properties = properties;
        this.gradePolicy = new GradePolicy(properties);
    }

    public long convertedRent(long deposit, long rent, Long mgmtFee, double rate) {
        BigDecimal depositPart = BigDecimal.valueOf(deposit)
                .multiply(BigDecimal.valueOf(rate))
                .divide(MONTHS_PER_YEAR, 10, RoundingMode.HALF_UP);
        return BigDecimal.valueOf(rent)
                .add(BigDecimal.valueOf(mgmtFee == null ? 0 : mgmtFee))
                .add(depositPart)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    public PriceResult evaluate(PriceInput input, Sample sample) {
        double rate = properties.conversionRate();
        long converted = convertedRent(input.deposit(), input.rent(), input.mgmtFee(), rate);

        if (sample == null || sample.medianPerM2() == null || sample.count() < properties.minSample()) {
            return pending(converted, input, rate, sample);
        }

        long fairRent = BigDecimal.valueOf(sample.medianPerM2())
                .multiply(BigDecimal.valueOf(input.areaM2()))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact()
                + properties.baselineMgmtFee();
        if (fairRent <= 0) {
            return pending(converted, input, rate, sample);
        }

        long monthlyExcess = converted - fairRent;
        long totalExcess = monthlyExcess * input.contractMonths();
        double excessRate = BigDecimal.valueOf(monthlyExcess)
                .divide(BigDecimal.valueOf(fairRent), 3, RoundingMode.HALF_UP)
                .doubleValue();

        return new PriceResult(converted, fairRent, monthlyExcess, input.contractMonths(), totalExcess,
                excessRate, gradePolicy.of(excessRate, false), rate, sample);
    }

    private PriceResult pending(long converted, PriceInput input, double rate, Sample sample) {
        return new PriceResult(converted, null, null, input.contractMonths(), null, null, Grade.PENDING, rate, sample);
    }
}
