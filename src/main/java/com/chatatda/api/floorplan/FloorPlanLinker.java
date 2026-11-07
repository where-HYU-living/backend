package com.chatatda.api.floorplan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
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

/**
 * 이미 시드된 DB 의 매물·기숙사 호실에 평면도 연결을 채워 넣는다.
 * 매물·기숙사 시드(FixtureSeeder)는 테이블이 비어 있을 때만 들어가서, 운영 DB 에 데이터가 먼저 있으면 시드 파일에 새로 적은
 * floorplanId 가 반영되지 않는다. 그 빈틈을 서버가 뜰 때마다 메운다.
 *
 * 안전 규칙: floorplan_id 가 비어 있고(null), 시드 파일에 값이 있고, 그 평면도가 floorplans 에 실제로 있을 때만 채운다.
 * 이미 값이 있는 행은 건드리지 않고, 몇 번을 돌려도 결과가 같다. FloorPlanSeeder(0)·FixtureSeeder(1) 뒤에 돈다.
 */
@Component
public class FloorPlanLinker {

    private static final Logger log = LoggerFactory.getLogger(FloorPlanLinker.class);

    /** 이번에 새로 연결한 행 수 */
    public record Linked(int listings, int dormRooms) {
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final boolean enabled;
    private final String location;

    public FloorPlanLinker(JdbcTemplate jdbc, ObjectMapper objectMapper, ResourceLoader resourceLoader,
                           @Value("${app.seed.enabled}") boolean enabled,
                           @Value("${app.seed.location}") String location) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.enabled = enabled;
        this.location = location.endsWith("/") ? location : location + "/";
    }

    @Order(4)
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (enabled) {
            link();
        }
    }

    public Linked link() {
        int listings = 0, dormRooms = 0;
        JsonNode listingFile = read("listings.json");
        if (listingFile != null) {
            for (JsonNode l : listingFile) {
                listings += linkRow("listings", l.path("id"), l.path("floorplanId"));
            }
        }
        JsonNode dormFile = read("dorms.json");
        if (dormFile != null) {
            for (JsonNode d : dormFile) {
                for (JsonNode r : d.path("roomTypes")) {
                    dormRooms += linkRow("dorm_room_types", r.path("id"), r.path("floorplanId"));
                }
            }
        }
        if (listings + dormRooms > 0) {
            log.info("평면도 연결 보충: 매물 {}건, 기숙사 호실 {}건", listings, dormRooms);
        }
        return new Linked(listings, dormRooms);
    }

    /** table 은 이 클래스 안의 고정 문자열만 쓴다 (사용자 입력 아님) */
    private int linkRow(String table, JsonNode id, JsonNode floorplanId) {
        if (!id.canConvertToLong() || !floorplanId.isIntegralNumber()) {
            return 0;
        }
        return jdbc.update("update " + table + " set floorplan_id = ? where id = ? and floorplan_id is null"
                + " and exists (select 1 from floorplans where id = ?)", floorplanId.asLong(), id.asLong(), floorplanId.asLong());
    }

    private JsonNode read(String fileName) {
        Resource resource = resourceLoader.getResource(location + fileName);
        if (!resource.exists()) {
            return null;
        }
        try (InputStream in = resource.getInputStream()) {
            return objectMapper.readTree(in);
        } catch (IOException e) {
            log.warn("{} 를 읽지 못해 평면도 연결 보충을 건너뜀: {}", fileName, e.getMessage());
            return null;
        }
    }
}
