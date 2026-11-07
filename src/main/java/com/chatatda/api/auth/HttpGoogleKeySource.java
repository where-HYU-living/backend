package com.chatatda.api.auth;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** 구글 공개키(JWKS)를 내려받아 1시간 캐시한다. 모르는 kid 가 오면 최대 1분에 한 번만 새로 받는다. */
@Component
public class HttpGoogleKeySource implements GoogleKeySource {

    private static final Duration TTL = Duration.ofHours(1);
    private static final Duration MIN_REFRESH = Duration.ofMinutes(1);

    private final String url;
    private final ObjectMapper mapper;
    private final Clock clock;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    private volatile Map<String, PublicKey> keys = Map.of();
    private volatile Instant fetchedAt = Instant.MIN;

    public HttpGoogleKeySource(@Value("${google.jwks-url:https://www.googleapis.com/oauth2/v3/certs}") String url,
                               ObjectMapper mapper, Clock clock) {
        this.url = url;
        this.mapper = mapper;
        this.clock = clock;
    }

    @Override
    public Optional<PublicKey> find(String keyId) {
        Instant now = clock.instant();
        boolean stale = fetchedAt.plus(TTL).isBefore(now);
        boolean unknown = keyId == null || !keys.containsKey(keyId);
        if ((stale || unknown) && fetchedAt.plus(MIN_REFRESH).isBefore(now)) {
            refresh(now);
        }
        return keyId == null ? Optional.empty() : Optional.ofNullable(keys.get(keyId));
    }

    private synchronized void refresh(Instant now) {
        if (fetchedAt.plus(MIN_REFRESH).isAfter(now)) {
            return;
        }
        try {
            HttpResponse<String> res = http.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(5)).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                throw new IllegalStateException("status " + res.statusCode());
            }
            keys = parse(mapper.readTree(res.body()));
            fetchedAt = now;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw upstream();
        } catch (Exception e) {
            fetchedAt = now; // 실패해도 1분은 다시 시도하지 않는다
            throw upstream();
        }
    }

    static Map<String, PublicKey> parse(JsonNode root) throws Exception {
        Map<String, PublicKey> out = new HashMap<>();
        KeyFactory rsa = KeyFactory.getInstance("RSA");
        for (JsonNode k : root.path("keys")) {
            if (!"RSA".equals(k.path("kty").asText())) {
                continue;
            }
            BigInteger n = new BigInteger(1, Base64.getUrlDecoder().decode(k.path("n").asText()));
            BigInteger e = new BigInteger(1, Base64.getUrlDecoder().decode(k.path("e").asText()));
            out.put(k.path("kid").asText(), rsa.generatePublic(new RSAPublicKeySpec(n, e)));
        }
        return Map.copyOf(out);
    }

    private ApiException upstream() {
        return new ApiException(ErrorCode.UPSTREAM_FAILED, "구글 서버에서 확인하지 못했어요. 잠시 뒤 다시 시도해 주세요.");
    }
}
