package com.chatatda.api.dorm;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.ErrorCode;
import com.chatatda.api.geo.WalkService;
import com.chatatda.api.user.ProfileService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class DormService {

    public record RoomTypeDto(long id, String name, int capacity, long semesterFee, int semesterMonths,
                              boolean mealIncluded, long monthlyCost, Long floorplanId) {

        static long monthlyCost(long semesterFee, int semesterMonths) {
            return Math.round((double) semesterFee / semesterMonths);
        }
    }

    public record DormDto(long id, String name, double lat, double lng, List<RoomTypeDto> roomTypes, Integer walkMin) {
    }

    private final JdbcTemplate jdbc;
    private final WalkService walk;
    private final ProfileService profiles;

    public DormService(JdbcTemplate jdbc, WalkService walk, ProfileService profiles) {
        this.jdbc = jdbc;
        this.walk = walk;
        this.profiles = profiles;
    }

    // 트랜잭션을 걸지 않는다: 읽기는 JDBC 한 번(자동 커밋)이고, 도보 시간은 TMAP 을 부를 수 있어 DB 연결을 쥔 채 기다리지 않게

    /** P1 기숙사 목록: 과 건물(buildingId, 없으면 로그인 사용자의 첫 건물)까지 도보 분을 함께 */
    public List<DormDto> list(Long buildingId, Long userIdOrNull) {
        Long effective = effectiveBuilding(buildingId, userIdOrNull);
        return load(null).stream()
                .map(d -> new DormDto(d.id(), d.name(), d.lat(), d.lng(), d.roomTypes(), walk.minutesOrNull(d.lat(), d.lng(), effective)))
                .toList();
    }

    public DormDto detail(long id, Long buildingId, Long userIdOrNull) {
        DormDto d = load(id).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "기숙사를 찾을 수 없어요."));
        Long effective = effectiveBuilding(buildingId, userIdOrNull);
        return new DormDto(d.id(), d.name(), d.lat(), d.lng(), d.roomTypes(), walk.minutesOrNull(d.lat(), d.lng(), effective));
    }

    private Long effectiveBuilding(Long buildingId, Long userIdOrNull) {
        return buildingId != null ? buildingId
                : userIdOrNull == null ? null : profiles.firstBuildingId(userIdOrNull).orElse(null);
    }

    private List<DormDto> load(Long id) {
        Map<Long, DormDto> dorms = new LinkedHashMap<>();
        String sql = """
                select d.id, d.name, d.lat, d.lng, r.id as room_id, r.name as room_name, r.capacity, r.semester_fee,
                       r.semester_months, r.meal_included, r.floorplan_id
                from dorms d left join dorm_room_types r on r.dorm_id = d.id
                """ + (id == null ? "" : "where d.id = ? ") + "order by d.id, r.id";
        Object[] args = id == null ? new Object[0] : new Object[] {id};
        jdbc.query(sql, rs -> {
            long dormId = rs.getLong("id");
            DormDto dorm = dorms.get(dormId);
            if (dorm == null) {
                dorm = new DormDto(dormId, rs.getString("name"), rs.getDouble("lat"), rs.getDouble("lng"), new ArrayList<>(), null);
                dorms.put(dormId, dorm);
            }
            Object roomId = rs.getObject("room_id");
            if (roomId != null) {
                long fee = rs.getLong("semester_fee");
                int months = rs.getInt("semester_months");
                dorm.roomTypes().add(new RoomTypeDto(((Number) roomId).longValue(), rs.getString("room_name"),
                        rs.getInt("capacity"), fee, months, rs.getBoolean("meal_included"),
                        RoomTypeDto.monthlyCost(fee, months), (Long) rs.getObject("floorplan_id")));
            }
        }, args);
        return List.copyOf(dorms.values());
    }
}
