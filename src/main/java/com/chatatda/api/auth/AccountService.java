package com.chatatda.api.auth;

import com.chatatda.api.common.JwtProvider;
import com.chatatda.api.user.User;
import com.chatatda.api.user.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.sql.Timestamp;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 로그인 수단(메일 코드, 구글)과 상관없이 같은 계정 생성·토큰 발급 규칙을 쓴다. */
@Service
public class AccountService {

    private static final int NICKNAME_TRIES = 50;

    public record LoginResult(String accessToken, long expiresIn, User user) {
    }

    private final UserRepository users;
    private final JwtProvider jwtProvider;
    private final Clock clock;
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();

    public AccountService(UserRepository users, JwtProvider jwtProvider, Clock clock, JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.users = users;
        this.jwtProvider = jwtProvider;
        this.clock = clock;
    }

    /** 이미 검증된 (정규화된) 이메일로 계정을 찾거나 처음이면 만들고 토큰을 발급한다. */
    @Transactional
    public LoginResult loginVerifiedEmail(String email) {
        Instant now = clock.instant();
        User user = users.findByEmail(email).orElse(null);
        // 처음 로그인을 두 번 눌러 같은 메일로 동시에 오면: 먼저 넣은 쪽만 들어가고 나머지는 그 계정을 다시 읽는다 (예전엔 500)
        for (int i = 0; user == null && i < NICKNAME_TRIES; i++) {
            jdbc.update("insert into users (email, nickname, created_at) values (?, ?, ?) on conflict do nothing",
                    email, newNickname(), Timestamp.from(now));
            user = users.findByEmail(email).orElse(null);
        }
        if (user == null) {
            throw new IllegalStateException("계정을 만들지 못했어요.");
        }
        return new LoginResult(jwtProvider.issue(user.getId()), JwtProvider.EXPIRES_IN_SECONDS, user);
    }

    private String newNickname() {
        for (int i = 0; i < NICKNAME_TRIES; i++) {
            String nickname = "한양" + String.format("%04d", random.nextInt(10_000));
            if (!users.existsByNickname(nickname)) {
                return nickname;
            }
        }
        throw new IllegalStateException("닉네임을 만들지 못했어요.");
    }
}
