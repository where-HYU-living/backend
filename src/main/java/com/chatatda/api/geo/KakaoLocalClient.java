package com.chatatda.api.geo;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class KakaoLocalClient {

    private static final int TIMEOUT_MS = 3_000;

    private final String baseUrl;
    private final String restKey;
    private final RestClient rest;

    public KakaoLocalClient(@Value("${external.kakao.base-url}") String baseUrl,
                            @Value("${external.kakao.rest-key:}") String restKey) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.restKey = restKey == null ? "" : restKey.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT_MS);
        factory.setReadTimeout(TIMEOUT_MS);
        this.rest = RestClient.builder().requestFactory(factory).build();
    }

    public String get(String path, Map<String, Object> query) {
        if (restKey.isEmpty()) {
            throw new ExternalApiException("external.kakao.rest-key (환경 변수 KAKAO_REST_KEY) 가 비어 있어요.");
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + path);
        query.forEach(builder::queryParam);
        URI uri = builder.encode().build().toUri();
        try {
            byte[] body = rest.get().uri(uri).header("Authorization", "KakaoAK " + restKey).retrieve().body(byte[].class);
            return body == null ? "" : new String(body, StandardCharsets.UTF_8);
        } catch (RestClientException e) {
            throw new ExternalApiException("카카오 로컬 호출 실패: " + e.getClass().getSimpleName(), e);
        }
    }
}
