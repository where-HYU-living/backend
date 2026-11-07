package com.chatatda.api.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.chatatda.api.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeoApiTest extends IntegrationTest {

    @Test
    void walk_ok() throws Exception {
        mvc.perform(get("/api/v1/walk").param("fromLat", "37.558").param("fromLng", "127.041").param("buildingId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minutes").value(7))
                .andExpect(jsonPath("$.distanceM").value(500))
                .andExpect(jsonPath("$.path[0][0]").value(37.558))
                .andExpect(jsonPath("$.path[0][1]").value(127.041))
                .andExpect(jsonPath("$.gateId").isEmpty());
    }

    @Test
    void walk_upstreamFails_502() throws Exception {
        walkPort.setFailing(true);
        mvc.perform(get("/api/v1/walk").param("fromLat", "37.558").param("fromLng", "127.041").param("buildingId", "101"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UPSTREAM_FAILED"));
    }

    @Test
    void walk_unknownBuilding_404_andMissingParam_400() throws Exception {
        mvc.perform(get("/api/v1/walk").param("fromLat", "37.558").param("fromLng", "127.041").param("buildingId", "999"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/walk").param("fromLat", "37.558").param("fromLng", "127.041"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details.buildingId").exists());
    }

    @Test
    void listing_walkMin_withBuilding() throws Exception {
        mvc.perform(get("/api/v1/listings").param("buildingId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[*].walkMin", everyItem(is(7))));
        mvc.perform(get("/api/v1/listings/201").param("buildingId", "101"))
                .andExpect(jsonPath("$.walkMin").value(7));
    }

    @Test
    void listing_walkMinNull_whenTmapDown() throws Exception {
        walkPort.setFailing(true);
        mvc.perform(get("/api/v1/listings").param("buildingId", "101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(6))
                .andExpect(jsonPath("$.items[*].walkMin", everyItem(nullValue())));
    }

    @Test
    void listing_walkFilter_usesMinutes() throws Exception {
        mvc.perform(get("/api/v1/listings").param("buildingId", "101").param("maxWalkMin", "7"))
                .andExpect(jsonPath("$.total").value(6));
        mvc.perform(get("/api/v1/listings").param("buildingId", "101").param("maxWalkMin", "6"))
                .andExpect(jsonPath("$.total").value(0));
    }

    @Test
    void nearby_combinesKakaoAndBusStops() throws Exception {
        placePort.setResult(NearbyCategory.CONVENIENCE, List.of(new Place(NearbyCategory.CONVENIENCE, "테스트 편의점", 37.5581, 127.0411, 15)));
        placePort.setResult(NearbyCategory.SUBWAY, List.of(new Place(NearbyCategory.SUBWAY, "테스트역", 37.5560, 127.0436, 320)));

        mvc.perform(get("/api/v1/listings/201/nearby"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].category").value("CONVENIENCE"))
                .andExpect(jsonPath("$[0].name").value("테스트 편의점"))
                .andExpect(jsonPath("$[1].category").value("BUS_STOP"))
                .andExpect(jsonPath("$[1].name").value("테스트 정류장 가까움"))
                .andExpect(jsonPath("$[2].name").value("테스트 정류장 조금 멂"))
                .andExpect(jsonPath("$[3].category").value("SUBWAY"));
        assertThat(placePort.lastRadius()).isEqualTo(300);
    }

    @Test
    void nearby_emptyWhenKakaoDown() throws Exception {
        placePort.setFailing(true);
        mvc.perform(get("/api/v1/listings/204/nearby"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void nearby_kakaoDown_stillReturnsBusStops() throws Exception {
        placePort.setFailing(true);
        mvc.perform(get("/api/v1/listings/201/nearby"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].category", everyItem(is("BUS_STOP"))))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void nearby_radiusCappedAt500_andInvalidRadius_400() throws Exception {
        mvc.perform(get("/api/v1/listings/201/nearby").param("radius", "2000")).andExpect(status().isOk());
        assertThat(placePort.lastRadius()).isEqualTo(500);

        mvc.perform(get("/api/v1/listings/201/nearby").param("radius", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/v1/listings/999999/nearby")).andExpect(status().isNotFound());
    }

    @Test
    void address_ok() throws Exception {
        addressPort.setResults(List.of(new AddressResult("서울 성동구 행당동 16-53", "서울 성동구 사근동길 6", "행당동", 37.5601, 127.0420)));
        mvc.perform(get("/api/v1/geo/address").param("q", "행당동 16-53"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dong").value("행당동"))
                .andExpect(jsonPath("$[0].roadAddress").value("서울 성동구 사근동길 6"))
                .andExpect(jsonPath("$[0].lat").value(37.5601));
    }

    @Test
    void address_upstreamFails_502_andBlank_400() throws Exception {
        addressPort.setFailing(true);
        mvc.perform(get("/api/v1/geo/address").param("q", "행당동"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.code").value("UPSTREAM_FAILED"));
        mvc.perform(get("/api/v1/geo/address").param("q", "  "))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/geo/address"))
                .andExpect(status().isBadRequest());
    }
}
