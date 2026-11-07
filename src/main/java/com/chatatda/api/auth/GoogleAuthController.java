package com.chatatda.api.auth;

import com.chatatda.api.auth.AuthController.UserView;
import com.chatatda.api.auth.AuthController.VerifyResponse;
import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.Locale;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 구글 로그인: 프론트가 받은 ID 토큰을 검증하고, 한양대 계정(@hanyang.ac.kr)일 때만 우리 토큰을 준다. */
@RestController
@RequestMapping("/api/v1/auth/google")
public class GoogleAuthController {

    private static final String DOMAIN = "@hanyang.ac.kr";

    public record GoogleLoginRequest(@NotBlank String idToken) {
    }

    private final GoogleTokenVerifier verifier;
    private final AccountService accounts;

    public GoogleAuthController(GoogleTokenVerifier verifier, AccountService accounts) {
        this.verifier = verifier;
        this.accounts = accounts;
    }

    @PostMapping
    public VerifyResponse login(@Valid @RequestBody GoogleLoginRequest request) {
        GoogleIdentity who = verifier.verify(request.idToken());
        if (!who.emailVerified()) {
            throw new ApiException(ErrorCode.UNAUTHORIZED, "구글 로그인을 확인하지 못했어요.");
        }
        String email = who.email().trim().toLowerCase(Locale.ROOT);
        // hd 는 구글이 워크스페이스 도메인을 알려주는 값. 이메일 도메인과 둘 다 맞아야 한다.
        boolean ok = email.length() > DOMAIN.length() && email.endsWith(DOMAIN)
                && email.indexOf('@') == email.length() - DOMAIN.length()
                && (who.hostedDomain() == null || "hanyang.ac.kr".equals(who.hostedDomain()));
        if (!ok) {
            throw new ApiException(ErrorCode.INVALID_EMAIL_DOMAIN, "한양대 메일(@hanyang.ac.kr)만 사용할 수 있어요.");
        }
        AccountService.LoginResult r = accounts.loginVerifiedEmail(email);
        return new VerifyResponse(r.accessToken(), r.expiresIn(),
                new UserView(r.user().getId(), r.user().getEmail(), r.user().getNickname()));
    }
}
