package com.chatatda.api.geo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class TmapWalkAdapter implements WalkPort {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int TIMEOUT_MS = 3_000;

    private final String baseUrl;
    private final String appKey;
    private final RestClient rest;

    public TmapWalkAdapter(@Value("${external.tmap.base-url}") String baseUrl,
                           @Value("${external.tmap.key:}") String appKey) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.appKey = appKey == null ? "" : appKey.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        this.rest = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public WalkResult route(double fromLat, double fromLng, double toLat, double toLng) {
        if (appKey.isEmpty()) {
            throw new ExternalApiException("external.tmap.key (환경 변수 TMAP_KEY) 가 비어 있어요.");
        }
        Map<String, String> body = Map.of(
                "startX", String.valueOf(fromLng), "startY", String.valueOf(fromLat),
                "endX", String.valueOf(toLng), "endY", String.valueOf(toLat),
                "startName", URLEncoder.encode("출발", StandardCharsets.UTF_8),
                "endName", URLEncoder.encode("도착", StandardCharsets.UTF_8),
                "reqCoordType", "WGS84GEO", "resCoordType", "WGS84GEO");
        try {
            byte[] response = rest.post()
                    .uri(baseUrl + "/tmap/routes/pedestrian?version=1")
                    .header("appKey", appKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(byte[].class);
            return parse(response == null ? "" : new String(response, StandardCharsets.UTF_8));
        } catch (RestClientException e) {
            throw new ExternalApiException("TMAP 호출 실패: " + e.getClass().getSimpleName(), e);
        }
    }

    static WalkResult parse(String json) {
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (JsonProcessingException e) {
            throw new ExternalApiException("TMAP 응답을 해석할 수 없어요.", e);
        }
        JsonNode features = root == null ? null : root.path("features");
        if (features == null || !features.isArray() || features.isEmpty()) {
            throw new ExternalApiException("TMAP 응답에 경로가 없어요.");
        }
        JsonNode summary = features.get(0).path("properties");
        if (!summary.has("totalTime") || !summary.has("totalDistance")) {
            throw new ExternalApiException("TMAP 응답에 시간·거리가 없어요.");
        }

        List<double[]> path = new ArrayList<>();
        for (JsonNode feature : features) {
            JsonNode geometry = feature.path("geometry");
            if (!"LineString".equals(geometry.path("type").asText())) {
                continue;
            }
            for (JsonNode coord : geometry.path("coordinates")) {
                double[] point = {coord.get(1).asDouble(), coord.get(0).asDouble()};
                if (path.isEmpty() || !Arrays.equals(path.get(path.size() - 1), point)) {
                    path.add(point);
                }
            }
        }
        int minutes = (int) Math.ceil(summary.get("totalTime").asInt() / 60.0);
        return new WalkResult(minutes, summary.get("totalDistance").asInt(), List.copyOf(path));
    }
}
