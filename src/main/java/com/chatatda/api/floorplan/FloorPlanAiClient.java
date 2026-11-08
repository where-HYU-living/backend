package com.chatatda.api.floorplan;

import com.chatatda.api.ai.AiServerClient;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

/** 평면도 AI 서버(FastAPI, ai-server/) 호출 */
@Component
public class FloorPlanAiClient {

    private final AiServerClient client;

    public FloorPlanAiClient(AiServerClient client) {
        this.client = client;
    }

    public boolean enabled() {
        return client.enabled();
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
        return client.post("/v1/floorplan/estimate-from-meta", body).path("floorplan");
    }
}
