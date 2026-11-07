package com.chatatda.api.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KakaoAdaptersTest {

    private final List<String> paths = new ArrayList<>();
    private final List<Map<String, Object>> params = new ArrayList<>();

    private KakaoLocalClient clientReturning(java.util.function.Function<Map<String, Object>, String> body) {
        return new KakaoLocalClient("https://dapi.kakao.com", "test-key") {
            @Override
            public String get(String path, Map<String, Object> query) {
                paths.add(path);
                params.add(query);
                return body.apply(query);
            }
        };
    }

    @Test
    void convenience_usesCategoryCs2_andParsesRealResponse() {
        var adapter = new KakaoPlaceAdapter(clientReturning(q -> GeoSamples.read("kakao-cs2.json")));

        List<Place> places = adapter.near(37.558, 127.041, 300, NearbyCategory.CONVENIENCE);

        assertThat(places).hasSize(10);
        Place first = places.get(0);
        assertThat(first.category()).isEqualTo(NearbyCategory.CONVENIENCE);
        assertThat(first.name()).isEqualTo("GS25 서울숲더샵점");
        assertThat(first.distanceM()).isEqualTo(86);
        assertThat(first.lat()).isEqualTo(37.5572215986032);
        assertThat(first.lng()).isEqualTo(127.040998020398);

        assertThat(paths).containsExactly("/v2/local/search/category.json");
        assertThat(params.get(0)).containsEntry("category_group_code", "CS2").containsEntry("radius", 300)
                .containsEntry("x", 127.041).containsEntry("y", 37.558).containsEntry("sort", "distance");
    }

    @Test
    void subway_usesCategorySw8() {
        var adapter = new KakaoPlaceAdapter(clientReturning(q -> GeoSamples.read("kakao-sw8.json")));

        List<Place> places = adapter.near(37.558, 127.041, 500, NearbyCategory.SUBWAY);

        assertThat(places).extracting(Place::name).containsExactly("한양대역 2호선", "왕십리역 수인분당선");
        assertThat(params.get(0)).containsEntry("category_group_code", "SW8");
    }

    @Test
    void laundry_searchesTwoKeywords_andRemovesDuplicates() {
        var adapter = new KakaoPlaceAdapter(clientReturning(q -> GeoSamples.read("kakao-laundry.json")));

        List<Place> places = adapter.near(37.558, 127.041, 300, NearbyCategory.LAUNDRY);

        assertThat(paths).containsOnly("/v2/local/search/keyword.json");
        assertThat(params).extracting(p -> p.get("query")).containsExactly("코인세탁", "빨래방");
        assertThat(places).singleElement().satisfies(p -> {
            assertThat(p.name()).isEqualTo("셀프빨래방");
            assertThat(p.category()).isEqualTo(NearbyCategory.LAUNDRY);
        });
    }

    @Test
    void busStop_isNotFromKakao() {
        var adapter = new KakaoPlaceAdapter(clientReturning(q -> "{}"));
        assertThatThrownBy(() -> adapter.near(37.558, 127.041, 300, NearbyCategory.BUS_STOP))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void address_usesLegalDong_notAdministrativeDong() {
        var adapter = new KakaoAddressAdapter(clientReturning(q -> GeoSamples.read("kakao-address-jibun.json")));

        List<AddressResult> results = adapter.search("행당동 16-53");

        assertThat(results).singleElement().satisfies(a -> {
            assertThat(a.dong()).isEqualTo("행당동");
            assertThat(a.address()).isEqualTo("서울 성동구 행당동 16-53");
            assertThat(a.roadAddress()).isEqualTo("서울 성동구 사근동길 6");
            assertThat(a.lat()).isEqualTo(37.5601221861853);
            assertThat(a.lng()).isEqualTo(127.042001595951);
        });
        assertThat(paths).containsExactly("/v2/local/search/address.json");
        assertThat(params.get(0)).containsEntry("query", "행당동 16-53");
    }

    @Test
    void address_roadQuery_alsoReturnsLegalDong() {
        var adapter = new KakaoAddressAdapter(clientReturning(q -> GeoSamples.read("kakao-address-road.json")));

        assertThat(adapter.search("왕십리로 222")).singleElement()
                .satisfies(a -> assertThat(a.dong()).isEqualTo("사근동"));
    }
}
