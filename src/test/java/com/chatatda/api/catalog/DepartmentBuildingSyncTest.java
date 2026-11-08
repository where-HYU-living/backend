package com.chatatda.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatatda.api.support.IntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * 운영 DB 처럼 학과가 이미 시드된 상태(test-fixtures: 학과 11~15, 건물 101~107)에서
 * 시드 파일에서 고친 기본 건물만 DB 에 반영되는지
 */
class DepartmentBuildingSyncTest extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ObjectMapper objectMapper;
    @Autowired
    ResourceLoader resourceLoader;
    @Autowired
    PlatformTransactionManager txManager;

    private DepartmentBuildingSync at(String location) {
        return new DepartmentBuildingSync(jdbc, objectMapper, resourceLoader, txManager, true, location);
    }

    private List<Long> buildingsOf(long departmentId) {
        return jdbc.queryForList("select building_id from department_buildings where department_id = ? order by position",
                Long.class, departmentId);
    }

    /** 다른 테스트가 test-fixtures 그대로라고 보므로 되돌린다 */
    @AfterEach
    void restore() {
        at("classpath:test-fixtures/").sync();
        jdbc.update("delete from buildings where id = 108");
    }

    @Test
    void updatesOnlyDepartmentsWhoseBuildingsChanged() {
        DepartmentBuildingSync.Synced synced = at("classpath:test-fixtures-dept-sync/").sync();

        assertThat(buildingsOf(11)).containsExactly(102L, 101L); // 순서만 바뀌어도 맞춘다 (맨 앞 = 대표 건물)
        assertThat(buildingsOf(12)).containsExactly(102L); // 같으면 그대로
        assertThat(buildingsOf(14)).containsExactly(108L, 104L); // 새 건물은 buildings 에 먼저 넣고 연결
        assertThat(synced).isEqualTo(new DepartmentBuildingSync.Synced(1, 2, 2));
    }

    @Test
    void skipsDepartmentsWithDifferentNameOrUnknownBuilding() {
        at("classpath:test-fixtures-dept-sync/").sync();

        assertThat(buildingsOf(13)).containsExactly(103L); // 이름이 다르면 다른 학과일 수 있어서 건드리지 않는다
        assertThat(buildingsOf(15)).containsExactly(104L, 103L); // 없는 건물(999)을 가리키게 하지 않는다
        assertThat(jdbc.queryForObject("select name from departments where id = 13", String.class)).isEqualTo("테스트 경영학과");
        assertThat(jdbc.queryForObject("select count(*) from departments where id = 99", Integer.class)).isZero(); // 학과를 새로 만들지 않는다
    }

    @Test
    void neverChangesExistingBuildings() {
        at("classpath:test-fixtures-dept-sync/").sync();

        assertThat(jdbc.queryForObject("select name from buildings where id = 101", String.class)).isEqualTo("테스트 공학관 A");
        assertThat(jdbc.queryForObject("select name from buildings where id = 108", String.class)).isEqualTo("테스트 새 건물");
    }

    @Test
    void isIdempotent() {
        at("classpath:test-fixtures-dept-sync/").sync();

        assertThat(at("classpath:test-fixtures-dept-sync/").sync()).isEqualTo(new DepartmentBuildingSync.Synced(0, 0, 2));
        assertThat(buildingsOf(11)).containsExactly(102L, 101L);
    }

    @Test
    void doesNothingWhenDatabaseAlreadyMatchesSeed() {
        assertThat(at("classpath:test-fixtures/").sync()).isEqualTo(new DepartmentBuildingSync.Synced(0, 0, 0));
    }

    @Test
    void doesNothingWhenSeedFilesAreMissing() {
        assertThat(at("classpath:no-such-fixtures/").sync()).isEqualTo(new DepartmentBuildingSync.Synced(0, 0, 0));
        assertThat(buildingsOf(11)).containsExactly(101L, 102L);
    }
}
