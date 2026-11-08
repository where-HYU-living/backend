package com.chatatda.api.floorplan;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** 평면도 AI 서버(FastAPI, ai-server/) 호출 */
@Component
public class FloorPlanAiClient {

    private final String baseUrl;
    private final RestClient restClient;

    public FloorPlanAiClient(@Value("${external.ai.enabled:false}") boolean enabled,
                             @Value("${external.ai.base-url:}") String baseUrl) {
        this.baseUrl = !enabled || baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(60_000); // 모델 추론은 오래 걸릴 수 있다
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    public boolean enabled() {
        return !baseUrl.isEmpty();
    }

    /** 도면 없이 면적·방 수로 추정 (루트 B). 응답의 floorplan 필드가 FloorPlan JSON 이다 */
    public JsonNode estimateFromMeta(long id, String name, double areaM2, Integer rooms) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("name", name);
        body.put("areaM2", areaM2);
        if (rooms != null) {
            body.put("rooms", rooms);
        }
        return post("/v1/floorplan/estimate-from-meta", body).path("floorplan");
    }

    private JsonNode post(String path, Object body) {
        if (!enabled()) {
            throw new ApiException(ErrorCode.UPSTREAM_FAILED, "평면도 AI 서버가 설정되지 않았어요.");
        }
        try {
            return restClient.post().uri(baseUrl + path).contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw new ApiException(ErrorCode.UPSTREAM_FAILED, "평면도 AI 서버에서 답을 받지 못했어요.");
        }
    }
}
