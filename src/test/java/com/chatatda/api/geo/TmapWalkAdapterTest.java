package com.chatatda.api.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class TmapWalkAdapterTest {

    @Test
    void parse_realResponse_minutesRoundedUp_andPathAsLatLng() {
        WalkResult r = TmapWalkAdapter.parse(GeoSamples.read("tmap-walk.json"));

        assertThat(r.minutes()).isEqualTo(9);
        assertThat(r.distanceM()).isEqualTo(611);
        assertThat(r.path()).hasSizeGreaterThan(2);
        assertThat(r.path().get(0)[0]).isCloseTo(37.558026, within(1e-5));
        assertThat(r.path().get(0)[1]).isCloseTo(127.041107, within(1e-5));
    }

    @Test
    void parse_removesConsecutiveDuplicatePoints() {
        WalkResult r = TmapWalkAdapter.parse(GeoSamples.read("tmap-walk.json"));
        for (int i = 1; i < r.path().size(); i++) {
            assertThat(Arrays.equals(r.path().get(i), r.path().get(i - 1))).as("point " + i).isFalse();
        }
    }

    @Test
    void parse_errorBody_throws() {
        assertThatThrownBy(() -> TmapWalkAdapter.parse("{\"error\":{\"code\":\"9401\"}}"))
                .isInstanceOf(ExternalApiException.class);
        assertThatThrownBy(() -> TmapWalkAdapter.parse("not json"))
                .isInstanceOf(ExternalApiException.class);
    }

    @Test
    void missingKey_failsOnlyWhenCalled() {
        var adapter = new TmapWalkAdapter("https://apis.openapi.sk.com", "");
        assertThatThrownBy(() -> adapter.route(37.5, 127.0, 37.6, 127.1))
                .isInstanceOf(ExternalApiException.class)
                .hasMessageContaining("external.tmap.key");
    }
}
