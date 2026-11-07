package com.chatatda.api.floorplan;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatatda.api.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** 운영 DB 처럼 매물·기숙사가 먼저 들어 있는 상태에서, 시드 파일의 평면도 연결이 빈 곳에만 채워지는지 */
class FloorPlanLinkerTest extends IntegrationTest {

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
    Clock clock;

    private FloorPlanLinker prod() {
        return new FloorPlanLinker(jdbc, objectMapper, resourceLoader, true, "classpath:fixtures/");
    }

    private void listing(long id, Long floorplanId) {
        jdbc.update("insert into listings (id, title, address, dong, lat, lng, deposit, rent, area_m2, room_type, floorplan_id) values (?, 't', 'a', 'd', 37.5, 127.0, 1, 1, 18, 'OPEN', ?)", id, floorplanId);
    }

    @BeforeEach
    void setUp() {
        // 운영 시드의 평면도(1~20, 101~107, 201~206)를 적재하고, 매물 4·6(비어 있음)·10(이미 5번 연결)과 기숙사 호실을 미리 넣어 둔다
        new FloorPlanSeeder(jdbc, objectMapper, resourceLoader, txManager, clock, true, "classpath:fixtures/").seed();
        listing(4, null);
        listing(6, null);
        listing(10, 5L);
        jdbc.update("insert into dorms (id, name, lat, lng) values (801, '제1학생생활관', 37.5, 127.0), (810, '제7학생생활관', 37.5, 127.0)");
        jdbc.update("insert into dorm_room_types (id, dorm_id, name, capacity, semester_fee, semester_months, meal_included, floorplan_id) values (8011, 801, '3인실', 3, 1, 4, false, null), (8101, 810, '2인실', 2, 1, 4, false, 7)");
    }

    /** 다른 테스트가 id 1 평면도만 있다고 보므로 되돌린다 */
    @AfterEach
    void cleanUp() {
        jdbc.update("delete from dorm_room_types where id in (8011, 8101)");
        jdbc.update("delete from dorms where id in (801, 810)");
        jdbc.update("delete from listings where id in (4, 6, 10)");
        seeder.seed();
    }

    private Long listingFp(long id) {
        return jdbc.queryForObject("select floorplan_id from listings where id = ?", Long.class, id);
    }

    private Long roomFp(long id) {
        return jdbc.queryForObject("select floorplan_id from dorm_room_types where id = ?", Long.class, id);
    }

    @Test
    void fillsOnlyEmptyLinks_andLeavesExistingValuesAlone() {
        FloorPlanLinker.Linked linked = prod().link();

        assertThat(listingFp(4)).isEqualTo(103L); // 비어 있던 매물 → 시드 파일의 값
        assertThat(listingFp(6)).isEqualTo(101L);
        assertThat(listingFp(10)).isEqualTo(5L); // 이미 값이 있으면 시드와 달라도 그대로
        assertThat(roomFp(8011)).isEqualTo(201L);
        assertThat(roomFp(8101)).isEqualTo(7L); // 이미 값이 있는 호실도 그대로
        assertThat(linked).isEqualTo(new FloorPlanLinker.Linked(2, 1));
    }

    @Test
    void isIdempotent() {
        prod().link();
        assertThat(prod().link()).isEqualTo(new FloorPlanLinker.Linked(0, 0));
        assertThat(listingFp(4)).isEqualTo(103L);
    }

    @Test
    void skipsWhenTheFloorplanIsNotLoaded() {
        jdbc.update("delete from floorplans where id = 103");

        prod().link();

        assertThat(listingFp(4)).isNull(); // 평면도가 없으면 연결하지 않는다 (없는 도면을 가리키지 않게)
        assertThat(listingFp(6)).isEqualTo(101L);
    }

    @Test
    void ignoresSeedRowsThatAreNotInTheDatabase() {
        // 시드 파일에는 매물 20건·기숙사 6호실이 있지만 DB 에는 일부만 있어도 오류 없이 있는 것만 채운다
        assertThat(prod().link().listings()).isEqualTo(2);
    }

    @Test
    void doesNothingWhenSeedFilesAreMissing() {
        FloorPlanLinker none = new FloorPlanLinker(jdbc, objectMapper, resourceLoader, true, "classpath:no-such-fixtures/");
        assertThat(none.link()).isEqualTo(new FloorPlanLinker.Linked(0, 0));
        assertThat(listingFp(4)).isNull();
    }
}
