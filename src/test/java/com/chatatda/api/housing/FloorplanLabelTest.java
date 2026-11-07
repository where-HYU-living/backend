package com.chatatda.api.housing;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatatda.api.housing.HousingIngestJob.Label;
import org.junit.jupiter.api.Test;

class FloorplanLabelTest {

    @Test
    void linksToUnitType_onlyWhenIdPresentAndNotListingUse() {
        assertThat(new Label("a", "19A", 19.6, 3L, null).linksToUnitType()).isTrue();
        assertThat(new Label("a", "19A", 19.6, 101L, "listing").linksToUnitType()).isFalse();
        assertThat(new Label("a", "19A", 19.6, 101L, "LISTING").linksToUnitType()).isFalse();
        assertThat(new Label("a", "19A", 19.6, null, null).linksToUnitType()).isFalse();
    }
}
