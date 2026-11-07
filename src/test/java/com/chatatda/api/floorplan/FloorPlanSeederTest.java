package com.chatatda.api.floorplan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chatatda.api.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class FloorPlanSeederTest extends IntegrationTest {

    @Autowired
    FloorPlanSeeder seeder;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    ResourceLoader resourceLoader;
    @Autowired
    PlatformTransactionManager txManager;
    @Autowired
    Clock clockBean;

    private FloorPlanSeeder at(String location) {
        return new FloorPlanSeeder(jdbc, objectMapper, resourceLoader, txManager, clockBean, true, location);
    }

    /** 다른 테스트가 id 1 평면도를 쓰므로 원래 시드로 되돌린다 */
    @AfterEach
    void restore() {
        seeder.seed();
    }

    @Test
    void startup_loadedFixtureFiles() {
        assertThat(jdbc.queryForList("select id from floorplans order by id", Long.class)).containsExactly(1L);
        assertThat(jdbc.queryForObject("select area_m2 from floorplans where id = 1", Double.class)).isEqualTo(18.22);
    }

    @Test
    void seed_isIdempotent_andOverwritesById() {
        jdbc.update("update floorplans set name = '손으로 바꾼 이름' where id = 1");

        seeder.seed();
        seeder.seed();

        assertThat(jdbc.queryForObject("select count(*) from floorplans", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select name from floorplans where id = 1", String.class)).isEqualTo("청아진 18타입");
    }

    @Test
    void planRemovedFromFiles_isRemovedFromTable() {
        at("classpath:test-fixtures-alt-floorplans/").seed();

        assertThat(jdbc.queryForList("select id from floorplans order by id", Long.class)).containsExactly(2L);
    }

    @Test
    void noFiles_keepsTableUntouched() {
        at("classpath:no-such-fixtures/").seed();

        assertThat(jdbc.queryForList("select id from floorplans", Long.class)).containsExactly(1L);
    }

    @Test
    void brokenFile_failsWithFileNameAndReasons_andChangesNothing() {
        assertThatThrownBy(() -> at("classpath:test-fixtures-broken-floorplans/").seed())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("bad.json")
                .hasMessageContaining("openings[0].wall")
                .hasMessageContaining("polygon");

        assertThat(jdbc.queryForList("select id from floorplans", Long.class)).containsExactly(1L);
    }

    @Test
    void productionFixtures_areValid_andLabelsPointToRealIdRanges() throws Exception {
        at("classpath:fixtures/").seed();
        assertThat(jdbc.queryForList("select id from floorplans order by id", Long.class)).contains(1L, 2L);

        var labels = objectMapper.readTree(resourceLoader.getResource("classpath:fixtures/floorplan-labels.json").getInputStream());
        var ids = new java.util.HashSet<Long>();
        int housing = 0, listing = 0;
        for (var l : labels) {
            long id = l.get("floorplanId").asLong();
            assertThat(ids.add(id)).as("라벨의 floorplanId %s 중복", id).isTrue();
            if ("listing".equals(l.path("use").asText())) {
                assertThat(id).isBetween(101L, 199L);
                listing++;
            } else {
                assertThat(id).isBetween(1L, 99L);
                housing++;
            }
        }
        assertThat(housing).isEqualTo(20);
        assertThat(listing).isEqualTo(7);

        var listings = objectMapper.readTree(resourceLoader.getResource("classpath:fixtures/listings.json").getInputStream());
        assertThat(listings).hasSize(7);
        for (var l : listings) {
            assertThat(ids).as("매물 %s 의 평면도", l.get("id")).contains(l.get("floorplanId").asLong());
            assertThat(l.get("floorplanId").asLong()).isBetween(101L, 199L);
        }

        // 기숙사 호실 유형 6종은 201~299 구간의 평면도를 하나씩 가리키고, 그 평면도가 실제로 적재돼 있어야 한다
        var dorms = objectMapper.readTree(resourceLoader.getResource("classpath:fixtures/dorms.json").getInputStream());
        var stored = new java.util.HashSet<>(jdbc.queryForList("select id from floorplans", Long.class));
        // 라벨(청년안심주택 20 + 매물 7)이 가리키는 평면도도 전부 실제 JSON 으로 적재돼 있어야 한다
        assertThat(stored).as("라벨이 가리키는 평면도가 JSON 에 없음").containsAll(ids);
        assertThat(stored).as("적재된 평면도 수 (20 + 7 + 6)").hasSize(33);
        int dormRooms = 0;
        for (var d : dorms) {
            for (var r : d.get("roomTypes")) {
                long fp = r.get("floorplanId").asLong();
                assertThat(fp).as("호실 %s 의 평면도", r.get("name").asText()).isBetween(201L, 299L);
                assertThat(stored).as("호실 %s 의 평면도 %s", r.get("name").asText(), fp).contains(fp);
                dormRooms++;
            }
        }
        assertThat(dormRooms).isEqualTo(6);
    }
}
