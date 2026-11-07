package com.chatatda.api.review;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.ErrorCode;
import com.chatatda.api.floorplan.FloorPlanService;
import com.chatatda.api.listing.ListingRepository;
import com.chatatda.api.review.ReviewDtos.ReviewItem;
import com.chatatda.api.review.ReviewDtos.ReviewList;
import com.chatatda.api.review.ReviewDtos.ReviewRequest;
import com.chatatda.api.user.User;
import com.chatatda.api.user.UserRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {

    private final ReviewRepository reviews;
    private final UserRepository users;
    private final ListingRepository listings;
    private final FloorPlanService floorPlans;
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public ReviewService(ReviewRepository reviews, UserRepository users, ListingRepository listings,
                         FloorPlanService floorPlans, JdbcTemplate jdbc, Clock clock) {
        this.reviews = reviews;
        this.users = users;
        this.listings = listings;
        this.floorPlans = floorPlans;
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ReviewList list(ReviewTargetType type, long targetId) {
        List<Review> rows = reviews.findByTargetTypeAndTargetIdOrderByCreatedAtDescIdDesc(type, targetId);
        Map<Long, String> nicknames = nicknames(rows.stream().map(Review::getAuthorId).collect(Collectors.toSet()));
        List<ReviewItem> items = rows.stream().map(r -> item(r, nicknames.get(r.getAuthorId()))).toList();
        Double average = rows.isEmpty() ? null : BigDecimal.valueOf(rows.stream().mapToInt(Review::getRating).average().orElse(0))
                .setScale(1, RoundingMode.HALF_UP).doubleValue();
        return new ReviewList(average, rows.size(), items);
    }

    @Transactional
    public ReviewItem create(long userId, ReviewRequest r) {
        if (!targetExists(r.targetType(), r.targetId())) {
            throw new ApiException(ErrorCode.NOT_FOUND, "후기를 남길 대상을 찾을 수 없어요.");
        }
        if (r.floorplanId() != null && !floorPlans.exists(r.floorplanId())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "요청 값이 올바르지 않아요.",
                    Map.of("floorplanId", "없는 평면도예요."));
        }
        double[] pin = r.pin() == null ? null : r.pin().stream().mapToDouble(Double::doubleValue).toArray();
        String text = r.text() == null || r.text().isBlank() ? null : r.text().trim();
        Review saved = reviews.save(new Review(userId, r.targetType(), r.targetId(), r.rating(), r.scores(), text, pin,
                r.floorplanId(), clock.instant()));
        return item(saved, nicknames(Set.of(userId)).get(userId));
    }

    private boolean targetExists(ReviewTargetType type, long id) {
        return switch (type) {
            case LISTING -> listings.existsById(id);
            case HOUSING -> count("select count(*) from housing where id = ?", id) > 0;
            case DORM -> count("select count(*) from dorms where id = ?", id) > 0;
        };
    }

    private int count(String sql, long id) {
        Integer n = jdbc.queryForObject(sql, Integer.class, id);
        return n == null ? 0 : n;
    }

    private static ReviewItem item(Review r, String nickname) {
        return new ReviewItem(r.getId(), r.getRating(), r.getScores(), r.getText(), r.getPin(), r.getFloorplanId(),
                nickname, r.getCreatedAt());
    }

    private Map<Long, String> nicknames(Set<Long> ids) {
        Map<Long, String> map = new HashMap<>();
        for (User u : users.findAllById(ids)) {
            map.put(u.getId(), u.getNickname());
        }
        return map;
    }
}
