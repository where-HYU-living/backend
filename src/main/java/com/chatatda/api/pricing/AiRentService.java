package com.chatatda.api.pricing;

import com.chatatda.api.ai.AiServerClient;
import com.chatatda.api.common.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/**
 * 매물의 AI 적정가. AI 서버가 켜져 있으면 /v1/rent/estimate 를 부르고,
 * 꺼져 있거나 답을 못 받으면 fixtures(ai-rent.json)의 값을 쓴다. 둘 다 없으면 null.
 */
@Service
public class AiRentService {

    private static final Logger log = LoggerFactory.getLogger(AiRentService.class);

    private final AiServerClient client;
    private final PricingProperties properties;
    private final Clock clock;
    private final Map<Long, AiEstimate> fixtures;

    public AiRentService(AiServerClient client, PricingProperties properties, Clock clock, ObjectMapper objectMapper,
                         ResourceLoader resourceLoader,
                         @Value("${app.ai-rent.location:classpath:fixtures/ai-rent.json}") String location) {
        this.client = client;
        this.properties = properties;
        this.clock = clock;
        this.fixtures = load(objectMapper, resourceLoader.getResource(location), location);
    }

    public AiEstimate forListing(long listingId, String dong, double areaM2, Integer floor, Integer builtYear) {
        if (client.enabled()) {
            try {
                return fromModel(dong, areaM2, floor, builtYear);
            } catch (ApiException | IllegalStateException e) {
                log.warn("AI 적정가 호출 실패 (매물 {}): {}", listingId, e.getMessage());
            }
        }
        return fixtures.get(listingId);
    }

    private AiEstimate fromModel(String dong, double areaM2, Integer floor, Integer builtYear) {
        YearMonth asOf = YearMonth.now(clock).minusMonths(1);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dong", dong);
        body.put("areaM2", areaM2);
        body.put("contractYm", asOf.getYear() * 100 + asOf.getMonthValue());
        body.put("floor", floor);
        body.put("builtYear", builtYear);
        JsonNode r = client.post("/v1/rent/estimate", body);
        if (r == null || !r.path("fairRent").canConvertToLong()) {
            throw new IllegalStateException("응답에 fairRent 가 없어요.");
        }
        // 모델은 실거래(관리비 없음) 기준이라 룰 기반 적정가처럼 기준 관리비를 더한다
        long mgmt = properties.baselineMgmtFee();
        long fair = r.path("fairRent").asLong();
        return new AiEstimate(fair + mgmt, longOr(r.path("low"), fair) + mgmt, longOr(r.path("high"), fair) + mgmt);
    }

    private static long longOr(JsonNode n, long fallback) {
        return n.canConvertToLong() ? n.asLong() : fallback;
    }

    private static Map<Long, AiEstimate> load(ObjectMapper objectMapper, Resource resource, String location) {
        Map<Long, AiEstimate> out = new HashMap<>();
        if (!resource.exists()) {
            return out;
        }
        try (InputStream in = resource.getInputStream()) {
            for (JsonNode n : objectMapper.readTree(in).path("listings")) {
                if (!n.path("fairRent").canConvertToLong()) {
                    continue; // 아직 값을 안 채운 매물
                }
                long fair = n.path("fairRent").asLong();
                out.put(n.path("listingId").asLong(),
                        new AiEstimate(fair, longOr(n.path("low"), fair), longOr(n.path("high"), fair)));
            }
        } catch (IOException e) {
            throw new IllegalStateException("AI 적정가 fixtures 를 읽지 못했어요: " + location, e);
        }
        return out;
    }
}
