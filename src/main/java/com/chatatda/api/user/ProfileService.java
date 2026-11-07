package com.chatatda.api.user;

import com.chatatda.api.catalog.BuildingRepository;
import com.chatatda.api.catalog.Department;
import com.chatatda.api.catalog.DepartmentRepository;
import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.ErrorCode;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    public record ProfileView(long id, String email, String nickname, Long departmentId,
                              List<Long> buildingIds, Budget budget) {
    }

    public record ProfileUpdate(Long departmentId, List<Long> buildingIds, Budget budget) {
    }

    private final UserRepository users;
    private final DepartmentRepository departments;
    private final BuildingRepository buildings;

    public ProfileService(UserRepository users, DepartmentRepository departments, BuildingRepository buildings) {
        this.users = users;
        this.departments = departments;
        this.buildings = buildings;
    }

    @Transactional(readOnly = true)
    public ProfileView get(long userId) {
        return view(load(userId));
    }

    @Transactional
    public ProfileView update(long userId, ProfileUpdate update) {
        User user = load(userId);

        if (update.departmentId() != null) {
            Department department = departments.findById(update.departmentId())
                    .orElseThrow(() -> invalid("departmentId", "없는 학과예요."));
            user.changeDepartment(department.getId());
            if (update.buildingIds() == null) {
                user.changeBuildings(department.getDefaultBuildingIds());
            }
        }
        if (update.buildingIds() != null) {
            List<Long> ids = List.copyOf(new LinkedHashSet<>(update.buildingIds()));
            if (buildings.countByIdIn(ids) != ids.size()) {
                throw invalid("buildingIds", "없는 건물이 있어요.");
            }
            user.changeBuildings(ids);
        }
        if (update.budget() != null) {
            user.changeBudget(update.budget());
        }
        return view(user);
    }

    @Transactional(readOnly = true)
    public Optional<Long> firstBuildingId(long userId) {
        return users.findById(userId)
                .flatMap(u -> u.getBuildingIds().stream().findFirst());
    }

    @Transactional(readOnly = true)
    public Optional<Budget> budget(long userId) {
        return users.findById(userId).map(User::getBudget);
    }

    private User load(long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED, "로그인이 필요해요."));
    }

    private ProfileView view(User user) {
        return new ProfileView(user.getId(), user.getEmail(), user.getNickname(), user.getDepartmentId(),
                List.copyOf(user.getBuildingIds()), user.getBudget());
    }

    private static ApiException invalid(String field, String reason) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "요청 값이 올바르지 않아요.", Map.of(field, reason));
    }
}
