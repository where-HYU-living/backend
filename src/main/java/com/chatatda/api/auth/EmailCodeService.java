package com.chatatda.api.auth;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.ErrorCode;
import com.chatatda.api.user.User;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailCodeService {

    private static final String DOMAIN = "@hanyang.ac.kr";
    private static final Duration CODE_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_INTERVAL = Duration.ofSeconds(60);
    private static final int MAX_ATTEMPTS = 5;
    /** 다 틀려 잠긴 메일은 1분이 아니라 15분 뒤에야 새 코드를 준다 (재전송으로 시도 횟수를 계속 되살리는 대입 공격을 늦춘다) */
    private static final Duration LOCKED_RESEND_INTERVAL = Duration.ofMinutes(15);

    private final EmailCodeRepository codes;
    private final AccountService accounts;
    private final MailPort mail;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public EmailCodeService(EmailCodeRepository codes, AccountService accounts, MailPort mail, Clock clock) {
        this.codes = codes;
        this.accounts = accounts;
        this.mail = mail;
        this.clock = clock;
    }

    public record LoginResult(String accessToken, long expiresIn, User user) {
    }

    @Transactional
    public void send(String rawEmail) {
        String email = normalize(rawEmail);
        requireHanyangEmail(email);
        Instant now = clock.instant();

        EmailCode existing = codes.findForUpdate(email).orElse(null);
        if (existing != null && existing.getAttempts() >= MAX_ATTEMPTS && existing.getSentAt().plus(LOCKED_RESEND_INTERVAL).isAfter(now)) {
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS, "인증 코드를 너무 많이 틀렸어요. 15분 뒤에 다시 받아 주세요.");
        }
        if (existing != null && existing.getSentAt().plus(RESEND_INTERVAL).isAfter(now)) {
            throw new ApiException(ErrorCode.TOO_MANY_REQUESTS, "인증 메일은 1분 뒤에 다시 보낼 수 있어요.");
        }

        String code = String.format("%06d", random.nextInt(1_000_000));
        String hash = hash(email, code);
        Instant expiresAt = now.plus(CODE_TTL);
        if (existing == null) {
            try {
                codes.saveAndFlush(new EmailCode(email, hash, now, expiresAt)); // 처음 보내는 메일이 동시에 오면 하나만 들어간다
            } catch (org.springframework.dao.DataIntegrityViolationException e) {
                throw new ApiException(ErrorCode.TOO_MANY_REQUESTS, "인증 메일은 1분 뒤에 다시 보낼 수 있어요.");
            }
        } else {
            existing.renew(hash, now, expiresAt);
        }

        try {
            mail.send(email, "[찾았다] 인증 코드", "인증 코드: " + code + "\n10분 안에 입력해 주세요.");
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.UPSTREAM_FAILED, "인증 메일을 보내지 못했어요. 잠시 뒤 다시 시도해 주세요.");
        }
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult verify(String rawEmail, String code) {
        String email = normalize(rawEmail);
        Instant now = clock.instant();

        EmailCode saved = codes.findForUpdate(email)
                .orElseThrow(() -> new ApiException(ErrorCode.CODE_MISMATCH, "인증 코드가 맞지 않아요."));
        if (saved.getAttempts() >= MAX_ATTEMPTS) {
            throw tooManyAttempts();
        }
        if (now.isAfter(saved.getExpiresAt())) {
            throw new ApiException(ErrorCode.CODE_EXPIRED, "인증 코드가 만료됐어요. 다시 받아 주세요.");
        }
        if (!matches(saved.getCodeHash(), hash(email, code))) {
            saved.recordFailure();
            if (saved.getAttempts() >= MAX_ATTEMPTS) {
                throw tooManyAttempts();
            }
            throw new ApiException(ErrorCode.CODE_MISMATCH, "인증 코드가 맞지 않아요.");
        }

        codes.delete(saved);
        AccountService.LoginResult result = accounts.loginVerifiedEmail(email);
        return new LoginResult(result.accessToken(), result.expiresIn(), result.user());
    }

    private ApiException tooManyAttempts() {
        return new ApiException(ErrorCode.TOO_MANY_REQUESTS, "인증 코드를 너무 많이 틀렸어요. 코드를 다시 받아 주세요.");
    }

    private String normalize(String rawEmail) {
        return rawEmail.trim().toLowerCase(Locale.ROOT);
    }

    private void requireHanyangEmail(String email) {
        boolean ok = email.length() > DOMAIN.length()
                && email.endsWith(DOMAIN)
                && email.indexOf('@') == email.length() - DOMAIN.length();
        if (!ok) {
            throw new ApiException(ErrorCode.INVALID_EMAIL_DOMAIN, "한양대 메일(@hanyang.ac.kr)만 사용할 수 있어요.");
        }
    }

    private String hash(String email, String code) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((email + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean matches(String expectedHash, String actualHash) {
        return MessageDigest.isEqual(
                expectedHash.getBytes(StandardCharsets.UTF_8), actualHash.getBytes(StandardCharsets.UTF_8));
    }
}
