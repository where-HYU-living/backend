package com.chatatda.api.auth;

import com.chatatda.api.user.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth/email")
// 메일 코드 로그인은 점검 스크립트·테스트용. 운영에서는 EMAIL_LOGIN_ENABLED=false 로 끈다 (화면 로그인은 구글뿐)
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "app.auth.email.enabled", havingValue = "true", matchIfMissing = true)
public class AuthController {

    public record SendRequest(@NotBlank @Email String email) {
    }

    public record VerifyRequest(@NotBlank @Email String email,
                                @NotBlank @Pattern(regexp = "\\d{6}", message = "숫자 6자리예요.") String code) {
    }

    public record UserView(long id, String email, String nickname) {
    }

    public record VerifyResponse(String accessToken, long expiresIn, UserView user) {
    }

    private final EmailCodeService service;

    public AuthController(EmailCodeService service) {
        this.service = service;
    }

    @PostMapping("/send")
    public ResponseEntity<Void> send(@Valid @RequestBody SendRequest request) {
        service.send(request.email());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/verify")
    public VerifyResponse verify(@Valid @RequestBody VerifyRequest request) {
        EmailCodeService.LoginResult result = service.verify(request.email(), request.code());
        User user = result.user();
        return new VerifyResponse(result.accessToken(), result.expiresIn(),
                new UserView(user.getId(), user.getEmail(), user.getNickname()));
    }
}
