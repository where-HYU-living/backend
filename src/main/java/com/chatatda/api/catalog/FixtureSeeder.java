package com.chatatda.api.catalog;

import com.chatatda.api.listing.RoomType;
import com.chatatda.api.user.User;
import com.chatatda.api.user.UserRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class FixtureSeeder {

    private static final Logger log = LoggerFactory.getLogger(FixtureSeeder.class);

    record BuildingFixture(Long id, String name, BuildingType type, Double lat, Double lng) {
    }

    record DepartmentFixture(Long id, String name, String college, List<Long> defaultBuildingIds) {
    }

    record ListingFixture(Long id, String title, String address, String dong, Double lat, Double lng,
                          Long deposit, Long rent, Long mgmtFee, Double areaM2, Integer floor, RoomType roomType,
                          String thumbnailUrl, List<String> options, List<String> photos, Long floorplanId,
                          Integer builtYear, String description) {
    }

    record DormRoomFixture(Long id, String name, Integer capacity, Long semesterFee, Integer semesterMonths,
                           Boolean mealIncluded, Long floorplanId) {
    }

    record DormFixture(Long id, String name, List<DormRoomFixture> roomTypes) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final TransactionTemplate tx;
    private final UserRepository users;
    private final Clock clock;
    private final boolean enabled;
    private final String location;

    public FixtureSeeder(JdbcTemplate jdbc, ObjectMapper objectMapper, ResourceLoader resourceLoader,
                         PlatformTransactionManager txManager, UserRepository users, Clock clock,
                         @Value("${app.seed.enabled}") boolean enabled,
                         @Value("${app.seed.location}") String location) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.tx = new TransactionTemplate(txManager);
        this.users = users;
        this.clock = clock;
        this.enabled = enabled;
        this.location = location.endsWith("/") ? location : location + "/";
    }

    @Order(1)
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (enabled) {
            seedIfEmpty();
        }
    }

    public void seedIfEmpty() {
        tx.executeWithoutResult(status -> {
            seedBuildings();
            seedDepartments();
            seedListings();
            seedDorms();
        });
    }

    public long demoUser(String nickname) {
        return users.findByNickname(nickname)
                .map(User::getId)
                .orElseGet(() -> users.save(
                        new User("demo-" + UUID.randomUUID() + "@demo.invalid", nickname, clock.instant())).getId());
    }

    private void seedBuildings() {
        if (!isEmpty("buildings")) {
            return;
        }
        Optional<List<BuildingFixture>> fixtures = read("buildings.json", new TypeReference<>() { });
        if (fixtures.isEmpty()) {
            return;
        }
        for (BuildingFixture b : fixtures.get()) {
            require(b.id() != null && b.name() != null && b.type() != null && b.lat() != null && b.lng() != null,
                    "buildings.json: id·name·type·lat·lng 는 필수예요. (id=" + b.id() + ")");
            jdbc.update("insert into buildings (id, name, type, lat, lng) values (?, ?, ?, ?, ?)",
                    b.id(), b.name(), b.type().name(), b.lat(), b.lng());
        }
        syncSequence("buildings");
        log.info("buildings 시드 {}건", fixtures.get().size());
    }

    private void seedDepartments() {
        if (!isEmpty("departments")) {
            return;
        }
        Optional<List<DepartmentFixture>> fixtures = read("departments.json", new TypeReference<>() { });
        if (fixtures.isEmpty()) {
            return;
        }
        Set<Long> buildingIds = new HashSet<>(jdbc.queryForList("select id from buildings", Long.class));
        for (DepartmentFixture d : fixtures.get()) {
            require(d.id() != null && d.name() != null && d.college() != null,
                    "departments.json: id·name·college 는 필수예요.");
            jdbc.update("insert into departments (id, name, college) values (?, ?, ?)", d.id(), d.name(), d.college());
            List<Long> defaults = d.defaultBuildingIds() == null ? List.of() : d.defaultBuildingIds();
            for (int i = 0; i < defaults.size(); i++) {
                Long buildingId = defaults.get(i);
                require(buildingIds.contains(buildingId),
                        "departments.json: 학과 " + d.id() + " 의 기본 건물 " + buildingId + " 가 buildings 에 없어요.");
                jdbc.update("insert into department_buildings (department_id, position, building_id) values (?, ?, ?)",
                        d.id(), i, buildingId);
            }
        }
        syncSequence("departments");
        log.info("departments 시드 {}건", fixtures.get().size());
    }

    private void seedListings() {
        if (!isEmpty("listings")) {
            return;
        }
        Optional<List<ListingFixture>> fixtures = read("listings.json", new TypeReference<>() { });
        if (fixtures.isEmpty()) {
            return;
        }
        for (ListingFixture l : fixtures.get()) {
            require(l.id() != null && l.title() != null && l.address() != null && l.dong() != null
                            && l.lat() != null && l.lng() != null && l.deposit() != null && l.rent() != null
                            && l.areaM2() != null && l.roomType() != null,
                    "listings.json: id·title·address·dong·lat·lng·deposit·rent·areaM2·roomType 은 필수예요. (id=" + l.id() + ")");
            jdbc.update("""
                            insert into listings (id, title, address, dong, lat, lng, deposit, rent, mgmt_fee, area_m2,
                                                  floor, room_type, thumbnail_url, floorplan_id, built_year, description)
                            values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                    l.id(), l.title(), l.address(), l.dong(), l.lat(), l.lng(), l.deposit(), l.rent(), l.mgmtFee(),
                    l.areaM2(), l.floor(), l.roomType().name(), l.thumbnailUrl(), l.floorplanId(), l.builtYear(),
                    l.description());
            insertOrdered("insert into listing_options (listing_id, position, option_name) values (?, ?, ?)", l.id(), l.options());
            insertOrdered("insert into listing_photos (listing_id, position, url) values (?, ?, ?)", l.id(), l.photos());
        }
        syncSequence("listings");
        log.info("listings 시드 {}건", fixtures.get().size());
    }

    private void seedDorms() {
        if (!isEmpty("dorms")) {
            return;
        }
        Optional<List<DormFixture>> fixtures = read("dorms.json", new TypeReference<>() { });
        if (fixtures.isEmpty()) {
            return;
        }
        for (DormFixture d : fixtures.get()) {
            require(d.id() != null && d.name() != null, "dorms.json: id·name 은 필수예요.");
            List<Map<String, Object>> building = jdbc.queryForList("select lat, lng from buildings where id = ?", d.id());
            require(!building.isEmpty(), "dorms.json: 기숙사 " + d.id() + " 와 같은 id 의 건물이 buildings 에 없어요.");
            jdbc.update("insert into dorms (id, name, lat, lng) values (?, ?, ?, ?)",
                    d.id(), d.name(), building.get(0).get("lat"), building.get(0).get("lng"));
            for (DormRoomFixture r : d.roomTypes() == null ? List.<DormRoomFixture>of() : d.roomTypes()) {
                require(r.id() != null && r.name() != null && r.capacity() != null && r.semesterFee() != null
                                && r.semesterMonths() != null && r.semesterMonths() > 0 && r.mealIncluded() != null,
                        "dorms.json: 호실 id·name·capacity·semesterFee·semesterMonths(1 이상)·mealIncluded 는 필수예요. (기숙사 " + d.id() + ")");
                jdbc.update("""
                                insert into dorm_room_types (id, dorm_id, name, capacity, semester_fee, semester_months,
                                                             meal_included, floorplan_id)
                                values (?, ?, ?, ?, ?, ?, ?, ?)""",
                        r.id(), d.id(), r.name(), r.capacity(), r.semesterFee(), r.semesterMonths(), r.mealIncluded(),
                        r.floorplanId());
            }
        }
        log.info("dorms 시드 {}건", fixtures.get().size());
    }

    private void insertOrdered(String sql, Long ownerId, List<String> values) {
        if (values == null) {
            return;
        }
        for (int i = 0; i < values.size(); i++) {
            jdbc.update(sql, ownerId, i, values.get(i));
        }
    }

    private boolean isEmpty(String table) {
        Integer count = jdbc.queryForObject("select count(*) from " + table, Integer.class);
        return count == null || count == 0;
    }

    private void syncSequence(String table) {
        jdbc.queryForObject("select setval(pg_get_serial_sequence(?, 'id'), (select max(id) from " + table + "))",
                Long.class, table);
    }

    private <T> Optional<T> read(String fileName, TypeReference<T> type) {
        Resource resource = resourceLoader.getResource(location + fileName);
        if (!resource.exists()) {
            log.warn("픽스처 {} 가 없어서 건너뜀 ({})", fileName, location);
            return Optional.empty();
        }
        try (InputStream in = resource.getInputStream()) {
            return Optional.of(objectMapper.readValue(in, type));
        } catch (IOException e) {
            throw new IllegalStateException("픽스처 " + fileName + " 를 읽지 못했어요.", e);
        }
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
