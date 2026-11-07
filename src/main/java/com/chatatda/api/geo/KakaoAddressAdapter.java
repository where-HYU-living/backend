package com.chatatda.api.geo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class KakaoAddressAdapter implements AddressPort {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final KakaoLocalClient client;

    public KakaoAddressAdapter(KakaoLocalClient client) {
        this.client = client;
    }

    @Override
    public List<AddressResult> search(String query) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("query", query);
        params.put("size", 10);
        return parse(client.get("/v2/local/search/address.json", params));
    }

    static List<AddressResult> parse(String json) {
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
        List<AddressResult> results = new ArrayList<>();
        for (JsonNode d : documents) {
            JsonNode address = d.path("address");
            String legalDong = address.path("region_3depth_name").asText("");
            if (!address.isObject() || legalDong.isBlank()) {
                continue;
            }
            JsonNode road = d.path("road_address");
            String roadAddress = road.isObject() ? road.path("address_name").asText(null) : null;
            results.add(new AddressResult(address.path("address_name").asText(), roadAddress, legalDong,
                    d.path("y").asDouble(), d.path("x").asDouble()));
        }
        return results;
    }
}
