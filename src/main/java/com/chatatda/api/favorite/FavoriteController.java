package com.chatatda.api.favorite;

import com.chatatda.api.common.CurrentUser;
import com.chatatda.api.favorite.FavoriteService.FavoriteItem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/favorites")
public class FavoriteController {

    public record AddRequest(@NotNull FavoriteType type, @NotNull Long targetId) {
    }

    private final FavoriteService service;

    public FavoriteController(FavoriteService service) {
        this.service = service;
    }

    @GetMapping
    public List<FavoriteItem> list(@CurrentUser Long userId, @RequestParam(required = false) FavoriteType type) {
        return service.list(userId, type);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FavoriteItem add(@CurrentUser Long userId, @Valid @RequestBody AddRequest request) {
        return service.add(userId, request.type(), request.targetId());
    }

    @DeleteMapping("/{type}/{targetId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@CurrentUser Long userId, @PathVariable FavoriteType type, @PathVariable long targetId) {
        service.remove(userId, type, targetId);
    }
}
