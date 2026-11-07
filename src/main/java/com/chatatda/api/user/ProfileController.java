package com.chatatda.api.user;

import com.chatatda.api.common.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me/profile")
public class ProfileController {

    public record BudgetRequest(@PositiveOrZero Long rentCap, @PositiveOrZero Long depositCap) {
    }

    public record ProfileRequest(Long departmentId, List<@NotNull Long> buildingIds, @Valid BudgetRequest budget) {
    }

    private final ProfileService service;

    public ProfileController(ProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ProfileService.ProfileView get(@CurrentUser Long userId) {
        return service.get(userId);
    }

    @PutMapping
    public ProfileService.ProfileView update(@CurrentUser Long userId, @Valid @RequestBody ProfileRequest request) {
        Budget budget = request.budget() == null ? null
                : new Budget(request.budget().rentCap(), request.budget().depositCap());
        return service.update(userId, new ProfileService.ProfileUpdate(request.departmentId(), request.buildingIds(), budget));
    }
}
