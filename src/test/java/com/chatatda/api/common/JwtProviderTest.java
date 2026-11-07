package com.chatatda.api.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chatatda.api.support.MutableClock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class JwtProviderTest {

    private static final String SECRET = "unit-secret-unit-secret-unit-secret-unit-secret";

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-07T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    private final JwtProvider jwt = new JwtProvider(clock, SECRET);

    @Test
    void issue_then_parse_returnsUserId() {
        assertThat(jwt.parse(jwt.issue(42L))).contains(42L);
    }

    @Test
    void valid_untilSevenDays() {
        String token = jwt.issue(1L);
        clock.advance(Duration.ofDays(7).minusSeconds(5));
        assertThat(jwt.parse(token)).contains(1L);
    }

    @Test
    void expired_afterSevenDays() {
        String token = jwt.issue(1L);
        clock.advance(Duration.ofDays(7).plusSeconds(5));
        assertThat(jwt.parse(token)).isEmpty();
    }

    @Test
    void tampered_isEmpty() {
        String token = jwt.issue(1L);
        assertThat(jwt.parse(token.substring(0, token.length() - 2) + "xx")).isEmpty();
    }

    @Test
    void signedWithOtherSecret_isEmpty() {
        JwtProvider other = new JwtProvider(clock, "other-secret-other-secret-other-secret-other-secret");
        assertThat(jwt.parse(other.issue(1L))).isEmpty();
    }

    @Test
    void garbage_isEmpty() {
        assertThat(jwt.parse("not-a-token")).isEmpty();
        assertThat(jwt.parse("")).isEmpty();
    }

    @Test
    void blankOrShortSecret_failsFast() {
        assertThatThrownBy(() -> new JwtProvider(clock, "")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtProvider(clock, "too-short")).isInstanceOf(IllegalStateException.class);
    }
}
