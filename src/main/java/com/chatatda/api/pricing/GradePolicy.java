package com.chatatda.api.pricing;

public class GradePolicy {

    private final PricingProperties properties;

    public GradePolicy(PricingProperties properties) {
        this.properties = properties;
    }

    public Grade of(Double excessRate, boolean pending) {
        if (pending || excessRate == null) {
            return Grade.PENDING;
        }
        if (excessRate <= properties.gradeFair()) {
            return Grade.FAIR;
        }
        if (excessRate <= properties.gradeSlight()) {
            return Grade.SLIGHTLY_HIGH;
        }
        if (excessRate <= properties.gradeHigh()) {
            return Grade.HIGH;
        }
        return Grade.CULPRIT;
    }
}
