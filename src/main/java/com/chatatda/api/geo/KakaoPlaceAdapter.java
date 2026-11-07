package com.chatatda.api.geo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class KakaoPlaceAdapter implements PlacePort {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final List<String> LAUNDRY_KEYWORDS = List.of("코인세탁", "빨래방");
    private static final int MAX_RESULTS = 15;

    private final KakaoLocalClient client;

    public KakaoPlaceAdapter(KakaoLocalClient client) {
        this.client = client;
    }

    @Override
    public List<Place> near(double lat, double lng, int radius, NearbyCategory category) {
        return switch (category) {
            case CONVENIENCE -> byCategory("CS2", lat, lng, radius, category);
            case SUBWAY -> byCategory("SW8", lat, lng, radius, category);
            case LAUNDRY -> byKeywords(LAUNDRY_KEYWORDS, lat, lng, radius, category);
            case BUS_STOP -> throw new IllegalArgumentException("버스정류장은 카카오가 아니라 BusStopIndex 에서 찾아요.");
        };
    }

    private List<Place> byCategory(String code, double lat, double lng, int radius, NearbyCategory category) {
        Map<String, Object> query = baseQuery(lat, lng, radius);
        query.put("category_group_code", code);
        return parse(client.get("/v2/local/search/category.json", query), category).stream().map(Found::place).toList();
    }

    private List<Place> byKeywords(List<String> keywords, double lat, double lng, int radius, NearbyCategory category) {
        Map<String, Found> merged = new LinkedHashMap<>();
        for (String keyword : keywords) {
            Map<String, Object> query = new LinkedHashMap<>();
            query.put("query", keyword);
            query.putAll(baseQuery(lat, lng, radius));
            for (Found f : parse(client.get("/v2/local/search/keyword.json", query), category)) {
                merged.putIfAbsent(f.id(), f);
            }
        }
        return merged.values().stream()
                .map(Found::place)
                .sorted(Comparator.comparingInt(Place::distanceM))
                .limit(MAX_RESULTS)
                .toList();
    }

    private static Map<String, Object> baseQuery(double lat, double lng, int radius) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("x", lng);
        query.put("y", lat);
        query.put("radius", radius);
        query.put("sort", "distance");
        query.put("size", MAX_RESULTS);
        return query;
    }

    record Found(String id, Place place) {
    }

    static List<Found> parse(String json, NearbyCategory category) {
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (JsonProcessingException e) {
            throw new ExternalApiException("카카오 응답을 해석할 수 없어요.", e);
        }
        JsonNode documents = root == null ? null : root.path("documents");
        if (documents == null || !documents.isArray()) {
            throw new ExternalApiException("카카오 응답에 documents 가 없어요.");
        }
        List<Found> found = new ArrayList<>();
        for (JsonNode d : documents) {
            String distance = d.path("distance").asText("");
            found.add(new Found(d.path("id").asText(), new Place(category, d.path("place_name").asText(),
                    d.path("y").asDouble(), d.path("x").asDouble(), distance.isBlank() ? 0 : Integer.parseInt(distance))));
        }
        return found;
    }
}
