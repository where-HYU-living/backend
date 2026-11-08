package com.chatatda.api.ai;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** AI 서버(FastAPI, ai-server/) 호출. 평면도·적정 월세가 같은 스위치(external.ai.enabled)를 쓴다 */
@Component
public class AiServerClient {

    private final String baseUrl;
    private final RestClient restClient;

    public AiServerClient(@Value("${external.ai.enabled:false}") boolean enabled,
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

    public JsonNode post(String path, Object body) {
        if (!enabled()) {
            throw new ApiException(ErrorCode.UPSTREAM_FAILED, "AI 서버가 설정되지 않았어요.");
        }
        try {
            return restClient.post().uri(baseUrl + path).contentType(MediaType.APPLICATION_JSON).body(body)
                    .retrieve().body(JsonNode.class);
        } catch (RestClientException e) {
            throw new ApiException(ErrorCode.UPSTREAM_FAILED, "AI 서버에서 답을 받지 못했어요.");
        }
    }
}
