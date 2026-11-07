package com.chatatda.api.pricing;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GradePolicyTest {

    private final GradePolicy policy = new GradePolicy(PriceCalculatorTest.DEFAULTS);

    @ParameterizedTest
    @CsvSource({"-0.2,FAIR", "0.05,FAIR", "0.0501,SLIGHTLY_HIGH", "0.15,SLIGHTLY_HIGH", "0.3,HIGH", "0.3001,CULPRIT"})
    void grade_boundaries(double rate, Grade expected) {
        assertThat(policy.of(rate, false)).isEqualTo(expected);
    }

    @Test
    void pendingFlagOrNullRate_isPending() {
        assertThat(policy.of(0.4, true)).isEqualTo(Grade.PENDING);
        assertThat(policy.of(null, false)).isEqualTo(Grade.PENDING);
    }
}
