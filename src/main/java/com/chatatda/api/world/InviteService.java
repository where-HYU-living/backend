package com.chatatda.api.world;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.ErrorCode;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/** 초대 토큰 (인메모리). 토큰 → 초대한 접속자 id. 서버를 다시 켜면 사라진다. */
@Service
public class InviteService {

    private static final int MAX_TOKENS = 5_000;

    private record Invite(String playerId, Instant expiresAt) {
    }

    private final Map<String, Invite> byToken = new ConcurrentHashMap<>();
    private final Map<String, String> tokenByPlayer = new ConcurrentHashMap<>();
    private final SecureRandom random = new SecureRandom();
    private final WorldProperties props;
    private final Clock clock;

    public InviteService(WorldProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
    }

    /** 같은 사람이 또 누르면 아직 유효한 토큰을 그대로 돌려준다. 접속 중인지는 호출하는 쪽에서 확인한다. */
    public String issue(String playerId) {
        Instant now = clock.instant();
        String existing = tokenByPlayer.get(playerId);
        if (existing != null) {
            Invite invite = byToken.get(existing);
            if (invite != null && invite.expiresAt().isAfter(now)) {
                return existing;
            }
        }
        purgeExpired(now);
        if (byToken.size() >= MAX_TOKENS) {
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS, "초대 링크를 너무 많이 만들었어요. 잠시 뒤에 다시 해 주세요.");
        }
        byte[] raw = new byte[16];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        byToken.put(token, new Invite(playerId, now.plus(props.inviteTtl())));
        tokenByPlayer.put(playerId, token);
        return token;
    }

    /** 토큰 만료 시각 (없거나 지났으면 null) */
    public Instant expiresAt(String token) {
        Invite invite = byToken.get(token);
        return invite == null ? null : invite.expiresAt();
    }

    /** 유효한 토큰이면 초대한 접속자 id. */
    public Optional<String> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Invite invite = byToken.get(token);
        if (invite == null || !invite.expiresAt().isAfter(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(invite.playerId());
    }

    /** 접속을 끊은 사람의 토큰은 바로 지운다 (접속마다 새 id 라 더는 쓸 수 없고, 남겨 두면 24시간 동안 토큰 자리를 차지한다) */
    public void revoke(String playerId) {
        String token = tokenByPlayer.remove(playerId);
        if (token != null) {
            byToken.remove(token);
        }
    }

    private void purgeExpired(Instant now) {
        byToken.entrySet().removeIf(e -> {
            boolean expired = !e.getValue().expiresAt().isAfter(now);
            if (expired) {
                tokenByPlayer.remove(e.getValue().playerId(), e.getKey());
            }
            return expired;
        });
    }
}
