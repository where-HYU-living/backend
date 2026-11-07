package com.chatatda.api.floorplan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * `fixtures/floorplans/*.json`(파일 하나 = FloorPlan 하나)을 읽어 floorplans 테이블에 넣는다.
 * 다른 시드와 달리 "비어 있을 때만"이 아니라 서버가 뜰 때마다 id 기준으로 덮어쓴다 — JSON 을 고치고 재시작하면 바로 반영된다.
 * 파일에서 사라진 평면도는 테이블에서도 지운다(파일이 하나도 없으면 아무것도 지우지 않는다).
 * 잘못된 파일이 하나라도 있으면 어느 파일의 무엇이 문제인지 알려 주며 서버가 뜨지 않는다.
 * 주택형 연결(HousingIngestJob)보다 먼저 돌도록 Order 0.
 */
@Component
public class FloorPlanSeeder {

    private static final Logger log = LoggerFactory.getLogger(FloorPlanSeeder.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ResourcePatternResolver resolver;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final boolean enabled;
    private final String location;

    public FloorPlanSeeder(JdbcTemplate jdbc, ObjectMapper objectMapper, ResourceLoader resourceLoader,
                           PlatformTransactionManager txManager, Clock clock,
                           @Value("${app.seed.enabled}") boolean enabled,
                           @Value("${app.seed.location}") String location) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.resolver = resourceLoader instanceof ResourcePatternResolver r ? r : new PathMatchingResourcePatternResolver(resourceLoader);
        this.tx = new TransactionTemplate(txManager);
        this.clock = clock;
        this.enabled = enabled;
        this.location = location.endsWith("/") ? location : location + "/";
    }

    @Order(0)
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (enabled) {
            seed();
        }
    }

    public void seed() {
        Resource[] files;
        try {
            files = resolver.getResources(location + "floorplans/*.json");
        } catch (IOException e) {
            log.warn("평면도 폴더 {}floorplans/ 를 읽지 못해 건너뜀: {}", location, e.getMessage());
            return;
        }
        if (files.length == 0) {
            log.warn("평면도 JSON 이 없어서 건너뜀 ({}floorplans/*.json)", location);
            return;
        }
        Arrays.sort(files, Comparator.comparing(r -> String.valueOf(r.getFilename())));

        Map<Long, JsonNode> plans = new HashMap<>();
        Map<Long, String> sources = new HashMap<>();
        List<String> problems = new ArrayList<>();
        for (Resource file : files) {
            String name = file.getFilename();
            JsonNode fp;
            try (InputStream in = file.getInputStream()) {
                fp = objectMapper.readTree(in);
            } catch (IOException e) {
                problems.add(name + ": JSON 을 읽지 못했어요 (" + e.getMessage() + ")");
                continue;
            }
            List<String> errors = FloorPlanValidator.validate(fp);
            errors.forEach(msg -> problems.add(name + ": " + msg));
            if (!errors.isEmpty()) {
                continue;
            }
            long id = fp.get("id").asLong();
            if (plans.containsKey(id)) {
                problems.add(name + ": id " + id + " 가 " + sources.get(id) + " 와 겹쳐요.");
                continue;
            }
            plans.put(id, fp);
            sources.put(id, name);
        }
        if (!problems.isEmpty()) {
            throw new IllegalStateException("평면도 JSON 에 문제가 있어요:\n - " + String.join("\n - ", problems));
        }

        Timestamp now = Timestamp.from(clock.instant());
        tx.executeWithoutResult(status -> {
            for (Map.Entry<Long, JsonNode> e : plans.entrySet()) {
                JsonNode fp = e.getValue();
                jdbc.update("""
                        insert into floorplans (id, name, area_m2, data, updated_at) values (?, ?, ?, ?::jsonb, ?)
                        on conflict (id) do update set name = excluded.name, area_m2 = excluded.area_m2,
                            data = excluded.data, updated_at = excluded.updated_at""",
                        e.getKey(), fp.get("name").asText(), fp.get("areaM2").asDouble(), fp.toString(), now);
            }
            Long[] ids = plans.keySet().toArray(new Long[0]);
            jdbc.update(con -> {
                var ps = con.prepareStatement("delete from floorplans where not (id = any (?))");
                ps.setArray(1, con.createArrayOf("bigint", ids));
                return ps;
            });
        });
        log.info("floorplans {}건 반영", plans.size());
    }
}
