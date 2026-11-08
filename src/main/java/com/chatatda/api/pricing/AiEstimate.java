package com.chatatda.api.pricing;

/** AI 적정가 (원/월, 환산 월세 기준 — 룰 기반 fairRent 와 같은 기준). low~high 는 적정 범위(25~75%) */
public record AiEstimate(long fairRent, long low, long high) {
}
