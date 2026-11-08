package com.chatatda.api.review;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.CurrentUser;
import com.chatatda.api.common.ErrorCode;
import com.chatatda.api.floorplan.FloorPlanService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * G1·G4 후기 (계약 5-8). 대상이 실제로 있는지 확인하고, 실내 핀(pin)은 평면도 좌표 [x, y, z] 그대로 저장한다.
 * floorplanId 는 핀을 찍은 방(평면도) 번호 (계약 v1.8). 실내에서는 같은 방의 핀만 그린다.
 */
@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    public enum TargetType { LISTING, HOUSING, DORM }

    public record Scores(@NotNull @Min(1) @Max(5) Integer noise, @NotNull @Min(1) @Max(5) Integer light,
                         @NotNull @Min(1) @Max(5) Integer bugs, @NotNull @Min(1) @Max(5) Integer landlord,
                         @NotNull @Min(1) @Max(5) Integer depositReturn) {
    }

    public record ReviewRequest(@NotNull TargetType targetType, @NotNull Long targetId,
                                @NotNull @Min(1) @Max(5) Integer rating, @NotNull @Valid Scores scores,
                                @NotNull @Size(max = 500) String text, List<Double> pin, Long floorplanId) {
    }

    public record ReviewItem(long id, int rating, Scores scores, String text, double[] pin, Long floorplanId,
                             String authorNickname, Instant createdAt) {
    }

    public record ReviewList(Double average, int count, List<ReviewItem> items) {
    }

    private static final String TABLE_FOR_LISTING = "listings";

    private final JdbcTemplate jdbc;
    private final FloorPlanService floorPlans;
    private final Clock clock;

    public ReviewController(JdbcTemplate jdbc, FloorPlanService floorPlans, Clock clock) {
        this.jdbc = jdbc;
        this.floorPlans = floorPlans;
        this.clock = clock;
    }

    @GetMapping
    public ReviewList list(@RequestParam TargetType targetType, @RequestParam long targetId) {
        requireTarget(targetType, targetId);
        List<ReviewItem> items = jdbc.query("""
                select r.*, u.nickname from reviews r join users u on u.id = r.user_id
                where r.target_type = ? and r.target_id = ? order by r.created_at desc, r.id desc limit 100""",
                (rs, i) -> new ReviewItem(rs.getLong("id"), rs.getInt("rating"),
                        new Scores(rs.getInt("noise"), rs.getInt("light"), rs.getInt("bugs"), rs.getInt("landlord"),
                                rs.getInt("deposit_return")),
                        rs.getString("text"),
                        rs.getObject("pin_x") == null ? null
                                : new double[]{rs.getDouble("pin_x"), rs.getDouble("pin_y"), rs.getDouble("pin_z")},
                        (Long) rs.getObject("floorplan_id"),
                        rs.getString("nickname"), rs.getTimestamp("created_at").toInstant()),
                targetType.name(), targetId);
        Map<String, Object> agg = jdbc.queryForMap(
                "select count(*) as n, avg(rating) as avg from reviews where target_type = ? and target_id = ?",
                targetType.name(), targetId);
        int count = ((Number) agg.get("n")).intValue();
        Double avg = agg.get("avg") == null ? null : Math.round(((Number) agg.get("avg")).doubleValue() * 10) / 10.0;
        return new ReviewList(avg, count, items);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public ReviewItem create(@CurrentUser Long userId, @Valid @RequestBody ReviewRequest req) {
        requireTarget(req.targetType(), req.targetId());
        if (req.pin() != null && (req.pin().size() != 3 || req.pin().stream().anyMatch(v -> v == null || !Double.isFinite(v)))) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "요청 값이 올바르지 않아요.", Map.of("pin", "[x, y, z] 숫자 3개여야 해요."));
        }
        if (req.floorplanId() != null && !floorPlans.exists(req.floorplanId())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "요청 값이 올바르지 않아요.", Map.of("floorplanId", "없는 평면도예요."));
        }
        Instant now = clock.instant();
        Double px = req.pin() == null ? null : req.pin().get(0);
        Double py = req.pin() == null ? null : req.pin().get(1);
        Double pz = req.pin() == null ? null : req.pin().get(2);
        Scores s = req.scores();
        long id = jdbc.queryForObject("""
                insert into reviews (user_id, target_type, target_id, rating, noise, light, bugs, landlord, deposit_return,
                                     text, pin_x, pin_y, pin_z, floorplan_id, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) returning id""", Long.class,
                userId, req.targetType().name(), req.targetId(), req.rating(), s.noise(), s.light(), s.bugs(),
                s.landlord(), s.depositReturn(), req.text().strip(), px, py, pz, req.floorplanId(), Timestamp.from(now));
        String nickname = jdbc.queryForObject("select nickname from users where id = ?", String.class, userId);
        return new ReviewItem(id, req.rating(), s, req.text().strip(),
                px == null ? null : new double[]{px, py, pz}, req.floorplanId(), nickname, now);
    }

    private void requireTarget(TargetType type, long id) {
        String table = switch (type) {
            case LISTING -> TABLE_FOR_LISTING;
            case HOUSING -> "housing";
            case DORM -> "dorms";
        };
        Integer n = jdbc.queryForObject("select count(*) from " + table + " where id = ?", Integer.class, id);
        if (n == null || n == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "후기를 남길 대상을 찾을 수 없어요.");
        }
    }
}
