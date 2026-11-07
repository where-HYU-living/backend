package com.chatatda.api.pricing;

public record PriceInput(long deposit, long rent, Long mgmtFee, double areaM2, String dong, int contractMonths) {
}
