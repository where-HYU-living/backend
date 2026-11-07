package com.chatatda.api.world;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invites")
public class InviteController {

    public record InviteRequest(@NotBlank String playerId) {
    }

    public record InviteResponse(String url, String token) {
    }

    private final InviteService invites;
    private final WorldService world;
    private final List<String> allowedOrigins;

    public InviteController(InviteService invites, WorldService world, @Value("${cors.allowed-origins}") List<String> allowedOrigins) {
        this.invites = invites;
        this.world = world;
        this.allowedOrigins = allowedOrigins;
    }

    /** 로그인 없이도 만들 수 있다 (게스트도 월드에 들어올 수 있으므로). */
    @PostMapping
    public InviteResponse create(@Valid @RequestBody InviteRequest request, HttpServletRequest http) {
        if (!world.contains(request.playerId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "접속 중인 사람만 초대 링크를 만들 수 있어요.");
        }
        String token = invites.issue(request.playerId());
        String origin = http.getHeader("Origin");
        String base = origin != null && allowedOrigins.contains(origin) ? origin : allowedOrigins.get(0);
        return new InviteResponse(base.replaceAll("/+$", "") + "/?invite=" + token, token);
    }
}
