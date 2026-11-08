package com.chatatda.api.housing;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.ErrorCode;
import com.chatatda.api.geo.WalkService;
import com.chatatda.api.housing.HousingDtos.AnnouncementDto;
import com.chatatda.api.housing.HousingDtos.EventDto;
import com.chatatda.api.housing.HousingDtos.HousingDetail;
import com.chatatda.api.housing.HousingDtos.HousingSummary;
import com.chatatda.api.housing.HousingDtos.UnitTypeDto;
import com.chatatda.api.listing.ListingQuery.Bbox;
import com.chatatda.api.pricing.PriceCalculator;
import com.chatatda.api.pricing.PricingProperties;
import com.chatatda.api.user.ProfileService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HousingService {

    public record HousingQuery(Long maxRent, Long maxDeposit, Integer maxWalkMin, Long buildingId, Bbox bbox) {
    }

    public record UnitOption(long housingId, String housingName, String unitName, long convertedRent, double lat,
                             double lng) {
    }

    public record EventOn(long eventId, long announcementId, long housingId, String housingName, EventType type,
                          LocalDate date) {
    }

    record HousingRow(long id, String name, String address, double lat, double lng, String contact,
                      String floorLayoutImageUrl) {
    }

    record UnitRow(long id, long housingId, String name, Double area, long deposit, long rent, Long mgmtFee,
                   Long floorplanId) {
    }

    record AnnouncementRow(long id, long housingId, String title, boolean additional, List<EventDto> events) {
    }

    private final JdbcTemplate jdbc;
    private final WalkService walk;
    private final ProfileService profiles;
    private final AnnouncementStatusPolicy statusPolicy;
    private final PriceCalculator calculator;
    private final PricingProperties properties;
    private final Clock clock;

    public HousingService(JdbcTemplate jdbc, WalkService walk, ProfileService profiles,
                          AnnouncementStatusPolicy statusPolicy, PricingProperties properties, Clock clock) {
        this.jdbc = jdbc;
        this.walk = walk;
        this.profiles = profiles;
        this.statusPolicy = statusPolicy;
        this.calculator = new PriceCalculator(properties);
        this.properties = properties;
        this.clock = clock;
    }

    // 트랜잭션 없이: 읽기는 모두 JDBC 한 번씩(자동 커밋), 도보 분은 TMAP 을 부를 수 있어 DB 연결을 쥔 채 기다리지 않게
    public List<HousingSummary> list(HousingQuery q, Long userIdOrNull) {
        Long buildingId = effectiveBuilding(q.buildingId(), userIdOrNull);
        Map<Long, List<UnitRow>> units = unitsByHousing();
        Map<Long, List<AnnouncementRow>> anns = announcementsByHousing(null);
        Set<Long> subscribed = subscribedIds(userIdOrNull);
        LocalDate today = LocalDate.now(clock);
        List<HousingSummary> result = new ArrayList<>();
        for (HousingRow h : housingRows(null)) {
            if (q.bbox() != null && !q.bbox().contains(h.lat(), h.lng())) {
                continue;
            }
            List<UnitRow> hu = units.getOrDefault(h.id(), List.of());
            if ((q.maxRent() != null || q.maxDeposit() != null) && hu.stream().noneMatch(u ->
                    (q.maxRent() == null || u.rent() <= q.maxRent()) && (q.maxDeposit() == null || u.deposit() <= q.maxDeposit()))) {
                continue;
            }
            Integer walkMin = walk.minutesOrNull(h.lat(), h.lng(), buildingId);
            if (q.maxWalkMin() != null && buildingId != null && (walkMin == null || walkMin > q.maxWalkMin())) {
                continue;
            }
            result.add(summary(h, hu, anns.getOrDefault(h.id(), List.of()), subscribed.contains(h.id()), walkMin, today));
        }
        return result;
    }

    // list 와 같은 이유로 트랜잭션 없이
    public HousingDetail detail(long id, Long buildingId, Long userIdOrNull) {
        HousingRow h = housingRows(id).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "청년안심주택을 찾을 수 없어요."));
        List<UnitRow> units = unitsByHousing().getOrDefault(id, List.of());
        Integer walkMin = walk.minutesOrNull(h.lat(), h.lng(), effectiveBuilding(buildingId, userIdOrNull));
        HousingSummary s = summary(h, units, announcementsByHousing(id).getOrDefault(id, List.of()),
                subscribedIds(userIdOrNull).contains(id), walkMin, LocalDate.now(clock));
        List<UnitTypeDto> unitDtos = units.stream().map(u -> new UnitTypeDto(u.id(), u.name(), u.area(), u.deposit(),
                u.rent(), u.mgmtFee(), converted(u), u.floorplanId())).toList();
        return new HousingDetail(s.id(), s.name(), s.address(), s.lat(), s.lng(), s.recruiting(), s.nextEvent(),
                s.subscribed(), s.minConvertedRent(), s.walkMin(), unitDtos, h.floorLayoutImageUrl(), h.contact());
    }

    @Transactional(readOnly = true)
    public List<AnnouncementDto> announcements(AnnouncementStatus status, Long housingId) {
        LocalDate today = LocalDate.now(clock);
        List<AnnouncementDto> result = new ArrayList<>();
        for (List<AnnouncementRow> rows : announcementsByHousing(housingId).values()) {
            for (AnnouncementRow a : rows) {
                AnnouncementStatus s = statusOf(a, today);
                if (status == null || status == s) {
                    result.add(new AnnouncementDto(a.id(), a.housingId(), a.title(), s, a.events()));
                }
            }
        }
        result.sort(Comparator.comparingLong(AnnouncementDto::id).reversed());
        return result;
    }

    @Transactional
    public void subscribe(long userId, long housingId) {
        Integer exists = jdbc.queryForObject("select count(*) from housing where id = ?", Integer.class, housingId);
        if (exists == null || exists == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "청년안심주택을 찾을 수 없어요.");
        }
        jdbc.update("""
                insert into housing_subscriptions (user_id, housing_id, created_at) values (?, ?, ?)
                on conflict do nothing""", userId, housingId, Timestamp.from(clock.instant()));
    }

    @Transactional
    public void unsubscribe(long userId, long housingId) {
        jdbc.update("delete from housing_subscriptions where user_id = ? and housing_id = ?", userId, housingId);
    }

    @Transactional(readOnly = true)
    public List<Long> subscriberIds(long housingId) {
        return jdbc.queryForList("select user_id from housing_subscriptions where housing_id = ? order by user_id",
                Long.class, housingId);
    }

    @Transactional(readOnly = true)
    public List<EventOn> eventsOn(LocalDate date) {
        return jdbc.query("""
                select e.id, e.announcement_id, a.housing_id, h.name, e.type, e.event_date
                from announcement_events e join announcements a on a.id = e.announcement_id join housing h on h.id = a.housing_id
                where e.event_date = ? order by e.id""",
                (rs, i) -> new EventOn(rs.getLong(1), rs.getLong(2), rs.getLong(3), rs.getString(4),
                        EventType.valueOf(rs.getString(5)), rs.getDate(6).toLocalDate()), date);
    }

    @Transactional(readOnly = true)
    public List<UnitOption> unitOptions() {
        Map<Long, HousingRow> housing = new LinkedHashMap<>();
        housingRows(null).forEach(h -> housing.put(h.id(), h));
        List<UnitOption> options = new ArrayList<>();
        unitsByHousing().forEach((housingId, units) -> {
            HousingRow h = housing.get(housingId);
            for (UnitRow u : units) {
                options.add(new UnitOption(housingId, h.name(), u.name(), converted(u), h.lat(), h.lng()));
            }
        });
        return options;
    }

    private HousingSummary summary(HousingRow h, List<UnitRow> units, List<AnnouncementRow> anns, boolean subscribed,
                                   Integer walkMin, LocalDate today) {
        boolean recruiting = anns.stream().map(a -> statusOf(a, today))
                .anyMatch(s -> s == AnnouncementStatus.OPEN || s == AnnouncementStatus.ADDITIONAL);
        EventDto next = anns.stream().flatMap(a -> a.events().stream())
                .filter(e -> !e.date().isBefore(today))
                .min(Comparator.comparing(EventDto::date).thenComparing(EventDto::type))
                .orElse(null);
        Long minConverted = units.stream().map(this::converted).min(Long::compare).orElse(null);
        return new HousingSummary(h.id(), h.name(), h.address(), h.lat(), h.lng(), recruiting, next, subscribed,
                minConverted, walkMin);
    }

    private AnnouncementStatus statusOf(AnnouncementRow a, LocalDate today) {
        Map<EventType, LocalDate> events = new EnumMap<>(EventType.class);
        a.events().forEach(e -> events.put(e.type(), e.date()));
        return statusPolicy.statusOf(events, a.additional(), today);
    }

    private long converted(UnitRow u) {
        return calculator.convertedRent(u.deposit(), u.rent(), u.mgmtFee(), properties.conversionRate());
    }

    private Long effectiveBuilding(Long buildingId, Long userIdOrNull) {
        if (buildingId != null) {
            return buildingId;
        }
        return userIdOrNull == null ? null : profiles.firstBuildingId(userIdOrNull).orElse(null);
    }

    private Set<Long> subscribedIds(Long userIdOrNull) {
        if (userIdOrNull == null) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList("select housing_id from housing_subscriptions where user_id = ?",
                Long.class, userIdOrNull));
    }

    private List<HousingRow> housingRows(Long id) {
        String sql = "select id, name, address, lat, lng, contact, floor_layout_image_url from housing"
                + (id == null ? "" : " where id = ?") + " order by id";
        Object[] args = id == null ? new Object[0] : new Object[] {id};
        return jdbc.query(sql, (rs, i) -> new HousingRow(rs.getLong("id"), rs.getString("name"), rs.getString("address"),
                rs.getDouble("lat"), rs.getDouble("lng"), rs.getString("contact"), rs.getString("floor_layout_image_url")), args);
    }

    private Map<Long, List<UnitRow>> unitsByHousing() {
        Map<Long, List<UnitRow>> map = new LinkedHashMap<>();
        jdbc.query("select * from housing_unit_types order by housing_id, id", rs -> {
            UnitRow u = new UnitRow(rs.getLong("id"), rs.getLong("housing_id"), rs.getString("name"),
                    (Double) rs.getObject("exclusive_area_m2"), rs.getLong("deposit"), rs.getLong("rent"),
                    (Long) rs.getObject("mgmt_fee"), (Long) rs.getObject("floorplan_id"));
            map.computeIfAbsent(u.housingId(), k -> new ArrayList<>()).add(u);
        });
        return map;
    }

    private Map<Long, List<AnnouncementRow>> announcementsByHousing(Long housingId) {
        Map<Long, AnnouncementRow> byId = new LinkedHashMap<>();
        String sql = """
                select a.id, a.housing_id, a.title, a.additional, e.type, e.event_date
                from announcements a left join announcement_events e on e.announcement_id = a.id
                """ + (housingId == null ? "" : "where a.housing_id = ? ") + "order by a.id, e.event_date, e.id";
        Object[] args = housingId == null ? new Object[0] : new Object[] {housingId};
        jdbc.query(sql, rs -> {
            long id = rs.getLong("id");
            AnnouncementRow row = byId.get(id);
            if (row == null) {
                row = new AnnouncementRow(id, rs.getLong("housing_id"), rs.getString("title"),
                        rs.getBoolean("additional"), new ArrayList<>());
                byId.put(id, row);
            }
            String type = rs.getString("type");
            if (type != null) {
                row.events().add(new EventDto(EventType.valueOf(type), rs.getDate("event_date").toLocalDate()));
            }
        }, args);
        Map<Long, List<AnnouncementRow>> map = new LinkedHashMap<>();
        byId.values().forEach(a -> map.computeIfAbsent(a.housingId(), k -> new ArrayList<>()).add(a));
        return map;
    }
}
