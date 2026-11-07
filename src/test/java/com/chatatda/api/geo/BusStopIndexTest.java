package com.chatatda.api.geo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class BusStopIndexTest {

    private final BusStopIndex index =
            new BusStopIndex(new ObjectMapper(), new DefaultResourceLoader(), "classpath:geo/bus-stops.json");

    @Test
    void realData_findsStopsNearHanyang_sortedByDistance() {
        List<Place> stops = index.near(37.5580, 127.0410, 300);

        assertThat(stops).isNotEmpty().hasSizeLessThanOrEqualTo(15);
        assertThat(stops).allSatisfy(s -> {
            assertThat(s.category()).isEqualTo(NearbyCategory.BUS_STOP);
            assertThat(s.distanceM()).isLessThanOrEqualTo(300);
        });
        assertThat(stops).extracting(Place::distanceM).isSorted();
        assertThat(stops).extracting(Place::name).contains("한양대앞");
    }

    @Test
    void missingFile_isEmptyNotError() {
        var empty = new BusStopIndex(new ObjectMapper(), new DefaultResourceLoader(), "classpath:geo/none.json");
        assertThat(empty.near(37.558, 127.041, 300)).isEmpty();
    }

    @Test
    void distance_isHaversine() {
        assertThat(GeoMath.distanceM(37.5580, 127.0410, 37.5585, 127.0410)).isBetween(54, 57);
    }
}
