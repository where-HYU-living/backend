package com.chatatda.api.catalog;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

/**
 * 이미 시드된 DB 의 학과 기본 건물(department_buildings)을 시드 파일(departments.json)에 맞춘다.
 * FixtureSeeder 는 테이블이 비어 있을 때만 넣어서, 운영 DB 에는 시드 파일에서 고친 학과 → 건물 매핑이 반영되지 않는다.
 * 그 빈틈을 서버가 뜰 때마다 메운다.
 *
 * 안전 규칙
 * - buildings.json 에 있는데 DB 에 없는 건물은 새로 넣는다. 이미 있는 건물(이름·좌표)은 건드리지 않는다.
 * - 학과는 DB 에 같은 id·같은 이름으로 있을 때만 맞춘다. 이름이 다르면(id 가 다른 학과를 가리킬 수 있음) 건너뛴다.
 *   학과 행 자체(이름·단과대)는 바꾸지 않고 새로 만들지도 않는다 (users.department_id 가 참조함).
 * - 기본 건물 중 하나라도 DB 에 없으면 그 학과는 건너뛴다.
 * - 이미 같으면 아무것도 안 해서 몇 번을 돌려도 결과가 같다. 사용자가 저장한 내 건물(user_buildings)은 건드리지 않는다.
 * FixtureSeeder(1) 뒤에 돈다.
 */
@Component
public class DepartmentBuildingSync {

    private static final Logger log = LoggerFactory.getLogger(DepartmentBuildingSync.class);

    /** buildingsAdded: 새로 넣은 건물, departmentsUpdated: 기본 건물을 바꾼 학과, skipped: 맞추지 못하고 건너뛴 학과 */
    public record Synced(int buildingsAdded, int departmentsUpdated, int skipped) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final TransactionTemplate tx;
    private final boolean enabled;
    private final String location;

    public DepartmentBuildingSync(JdbcTemplate jdbc, ObjectMapper objectMapper, ResourceLoader resourceLoader,
                                  PlatformTransactionManager txManager,
                                  @Value("${app.seed.enabled}") boolean enabled,
                                  @Value("${app.seed.location}") String location) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.tx = new TransactionTemplate(txManager);
        this.enabled = enabled;
        this.location = location.endsWith("/") ? location : location + "/";
    }

    @Order(5)
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (enabled) {
            sync();
        }
    }

    public Synced sync() {
        JsonNode departmentFile = read("departments.json");
        if (departmentFile == null) {
            return new Synced(0, 0, 0);
        }
        JsonNode buildingFile = read("buildings.json");
        Synced result = tx.execute(status -> {
            int added = buildingFile == null ? 0 : addMissingBuildings(buildingFile);
            return syncDepartments(departmentFile, added);
        });
        if (result.buildingsAdded() + result.departmentsUpdated() + result.skipped() > 0) {
            log.info("학과 기본 건물 맞춤: 건물 추가 {}건, 학과 갱신 {}건, 건너뜀 {}건",
                    result.buildingsAdded(), result.departmentsUpdated(), result.skipped());
        }
        return result;
    }

    private int addMissingBuildings(JsonNode file) {
        int added = 0;
        for (JsonNode b : file) {
            if (!b.path("id").canConvertToLong() || !b.path("name").isTextual() || !b.path("type").isTextual()
                    || !b.path("lat").isNumber() || !b.path("lng").isNumber()) {
                continue;
            }
            added += jdbc.update("insert into buildings (id, name, type, lat, lng) values (?, ?, ?, ?, ?) on conflict (id) do nothing",
                    b.path("id").asLong(), b.path("name").asText(), b.path("type").asText(),
                    b.path("lat").asDouble(), b.path("lng").asDouble());
        }
        if (added > 0) {
            jdbc.queryForObject("select setval(pg_get_serial_sequence('buildings', 'id'), (select max(id) from buildings))", Long.class);
        }
        return added;
    }

    private Synced syncDepartments(JsonNode file, int buildingsAdded) {
        Set<Long> buildingIds = new HashSet<>(jdbc.queryForList("select id from buildings", Long.class));
        Map<Long, String> names = new HashMap<>();
        jdbc.query("select id, name from departments", rs -> {
            names.put(rs.getLong("id"), rs.getString("name"));
        });
        int updated = 0, skipped = 0;
        for (JsonNode d : file) {
            if (!d.path("id").canConvertToLong()) {
                continue;
            }
            long id = d.path("id").asLong();
            String name = d.path("name").asText(null);
            if (!names.containsKey(id)) {
                continue; // DB 에 없는 학과는 만들지 않는다 (빈 DB 는 FixtureSeeder 몫)
            }
            if (!names.get(id).equals(name)) {
                log.warn("학과 {} 이름이 DB('{}')와 시드('{}')가 달라서 기본 건물 맞춤을 건너뜀", id, names.get(id), name);
                skipped++;
                continue;
            }
            List<Long> want = new ArrayList<>();
            d.path("defaultBuildingIds").forEach(b -> want.add(b.asLong()));
            List<Long> missing = want.stream().filter(b -> !buildingIds.contains(b)).toList();
            if (!missing.isEmpty()) {
                log.warn("학과 {}({}) 의 기본 건물 {} 가 buildings 에 없어서 건너뜀", id, name, missing);
                skipped++;
                continue;
            }
            List<Long> have = jdbc.queryForList(
                    "select building_id from department_buildings where department_id = ? order by position", Long.class, id);
            if (have.equals(want)) {
                continue;
            }
            jdbc.update("delete from department_buildings where department_id = ?", id);
            for (int i = 0; i < want.size(); i++) {
                jdbc.update("insert into department_buildings (department_id, position, building_id) values (?, ?, ?)",
                        id, i, want.get(i));
            }
            updated++;
        }
        return new Synced(buildingsAdded, updated, skipped);
    }

    private JsonNode read(String fileName) {
        Resource resource = resourceLoader.getResource(location + fileName);
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readTree(in);
        } catch (IOException e) {
            log.warn("{} 를 읽지 못해 학과 기본 건물 맞춤을 건너뜀: {}", fileName, e.getMessage());
            return null;
        }
    }
}
