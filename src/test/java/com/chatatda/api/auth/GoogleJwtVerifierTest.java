package com.chatatda.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.ErrorCode;
import io.jsonwebtoken.Jwts;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class GoogleJwtVerifierTest {

    private static final String CLIENT_ID = "cid.apps.googleusercontent.com";
    private static final Instant NOW = Instant.parse("2026-11-07T00:00:00Z");
    private static KeyPair good;
    private static KeyPair other;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
    private final GoogleKeySource keys = kid -> "k1".equals(kid) ? Optional.<PublicKey>of(good.getPublic()) : Optional.empty();
    private final GoogleJwtVerifier verifier = new GoogleJwtVerifier(keys, clock, CLIENT_ID);

    @BeforeAll
    static void keys() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        good = g.generateKeyPair();
        other = g.generateKeyPair();
    }

    private String token(KeyPair signer, String kid, String aud, String iss, Instant exp, Map<String, Object> extra) {
        return Jwts.builder().header().keyId(kid).and()
                .issuer(iss).audience().add(aud).and().subject("sub1")
                .issuedAt(Date.from(NOW.minusSeconds(60))).expiration(Date.from(exp))
                .claims(extra).signWith(signer.getPrivate(), Jwts.SIG.RS256).compact();
    }

    private String valid(Map<String, Object> extra) {
        return token(good, "k1", CLIENT_ID, "https://accounts.google.com", NOW.plusSeconds(3600), extra);
    }

    @Test
    void validToken_returnsIdentity() {
        GoogleIdentity id = verifier.verify(valid(Map.of("email", "a@hanyang.ac.kr", "email_verified", true, "hd", "hanyang.ac.kr")));
        assertThat(id).isEqualTo(new GoogleIdentity("a@hanyang.ac.kr", true, "hanyang.ac.kr"));
    }

    @Test
    void bothIssuerSpellingsAccepted() {
        String t = token(good, "k1", CLIENT_ID, "accounts.google.com", NOW.plusSeconds(60), Map.of("email", "a@hanyang.ac.kr", "email_verified", true));
        assertThat(verifier.verify(t).email()).isEqualTo("a@hanyang.ac.kr");
    }

    @Test
    void rejectsWrongAudience_expired_badSignature_unknownKid_wrongIssuer() {
        Map<String, Object> ok = Map.of("email", "a@hanyang.ac.kr", "email_verified", true);
        String[] bad = {
            token(good, "k1", "someone-else", "https://accounts.google.com", NOW.plusSeconds(60), ok),
            token(good, "k1", CLIENT_ID, "https://accounts.google.com", NOW.minusSeconds(10), ok),
            token(other, "k1", CLIENT_ID, "https://accounts.google.com", NOW.plusSeconds(60), ok),
            token(good, "nope", CLIENT_ID, "https://accounts.google.com", NOW.plusSeconds(60), ok),
            token(good, "k1", CLIENT_ID, "https://evil.example.com", NOW.plusSeconds(60), ok),
            "not.a.jwt",
        };
        for (String t : bad) {
            assertThatThrownBy(() -> verifier.verify(t)).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UNAUTHORIZED));
        }
    }

    @Test
    void unsignedTokenRejected() {
        String unsigned = Jwts.builder().issuer("https://accounts.google.com").audience().add(CLIENT_ID).and().claim("email", "a@hanyang.ac.kr").compact();
        assertThatThrownBy(() -> verifier.verify(unsigned)).isInstanceOf(ApiException.class);
    }

    @Test
    void notConfigured_is502() {
        GoogleJwtVerifier none = new GoogleJwtVerifier(keys, clock, "");
        assertThatThrownBy(() -> none.verify("x")).isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.UPSTREAM_FAILED));
    }
}
