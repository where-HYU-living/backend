package com.chatatda.api.pricing;

public record PriceResult(long convertedRent, Long fairRent, Long monthlyExcess, int contractMonths,
                          Long totalExcess, Double excessRate, Grade grade, double conversionRate, Sample sample) {
}
