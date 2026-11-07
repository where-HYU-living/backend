package com.chatatda.api.auth;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.ErrorCode;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.LocatorAdapter;
import java.security.Key;
import java.time.Clock;
import java.util.Date;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class GoogleJwtVerifier implements GoogleTokenVerifier {

    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final GoogleKeySource keys;
    private final Clock clock;
    private final String clientId;

    public GoogleJwtVerifier(GoogleKeySource keys, Clock clock, @Value("${google.client-id:}") String clientId) {
        this.keys = keys;
        this.clock = clock;
        this.clientId = clientId == null ? "" : clientId.trim();
    }

    @Override
    public GoogleIdentity verify(String idToken) {
        if (clientId.isEmpty()) {
            throw new ApiException(ErrorCode.UPSTREAM_FAILED, "구글 로그인이 아직 설정되지 않았어요.");
        }
        Claims claims;
        try {
            claims = Jwts.parser()
                    .keyLocator(new LocatorAdapter<Key>() {
                        @Override
                        protected Key locate(JwsHeader header) {
                            if (!"RS256".equals(header.getAlgorithm())) {
                                return null;
                            }
                            return keys.find(header.getKeyId()).orElse(null);
                        }
                    })
                    .clock(() -> Date.from(clock.instant()))
                    .requireAudience(clientId)
                    .build()
                    .parseSignedClaims(idToken)
                    .getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw invalid();
        }
        if (claims.getIssuer() == null || !ISSUERS.contains(claims.getIssuer())) {
            throw invalid();
        }
        String email = claims.get("email", String.class);
        if (email == null || email.isBlank()) {
            throw invalid();
        }
        return new GoogleIdentity(email, Boolean.TRUE.equals(claims.get("email_verified", Boolean.class)),
                claims.get("hd", String.class));
    }

    private ApiException invalid() {
        return new ApiException(ErrorCode.UNAUTHORIZED, "구글 로그인을 확인하지 못했어요.");
    }
}
