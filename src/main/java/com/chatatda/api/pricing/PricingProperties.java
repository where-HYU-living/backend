package com.chatatda.api.pricing;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("pricing")
public record PricingProperties(
        double conversionRate,
        double gradeFair,
        double gradeSlight,
        double gradeHigh,
        int minSample,
        double areaTolerance,
        int periodMonths,
        int defaultContractMonths,
        long baselineMgmtFee) {

    public PricingProperties {
        require(conversionRate > 0 && conversionRate < 1, "pricing.conversion-rate 는 0과 1 사이여야 해요.");
        require(gradeFair >= 0 && gradeFair < gradeSlight && gradeSlight < gradeHigh,
                "pricing.grade-* 는 0 ≤ fair < slight < high 순서여야 해요.");
        require(minSample >= 1, "pricing.min-sample 은 1 이상이어야 해요.");
        require(areaTolerance > 0 && areaTolerance < 1, "pricing.area-tolerance 는 0과 1 사이여야 해요.");
        require(periodMonths >= 1, "pricing.period-months 는 1 이상이어야 해요.");
        require(defaultContractMonths >= 1, "pricing.default-contract-months 는 1 이상이어야 해요.");
        require(baselineMgmtFee >= 0, "pricing.baseline-mgmt-fee 는 0 이상이어야 해요.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
