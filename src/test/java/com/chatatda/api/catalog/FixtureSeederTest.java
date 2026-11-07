package com.chatatda.api.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.chatatda.api.support.IntegrationTest;
import com.chatatda.api.user.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

class FixtureSeederTest extends IntegrationTest {

    @Autowired
    FixtureSeeder seeder;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    ResourceLoader resourceLoader;

    @Autowired
    PlatformTransactionManager txManager;

    @Autowired
    UserRepository users;

    @Autowired
    Clock clockBean;

    @Test
    void seeder_isIdempotent() {
        int departments = count("departments");
        int buildings = count("buildings");

        seeder.seedIfEmpty();
        seeder.seedIfEmpty();

        assertThat(count("departments")).isEqualTo(departments).isEqualTo(5);
        assertThat(count("buildings")).isEqualTo(buildings).isEqualTo(7);
    }

    @Test
    void seeder_keepsFixtureIds() {
        assertThat(jdbc.queryForObject("select name from departments where id = 11", String.class))
                .isEqualTo("테스트 컴퓨터학과");
        assertThat(jdbc.queryForObject("select name from buildings where id = 107", String.class))
                .isEqualTo("테스트 기숙사");

        Long newId = jdbc.queryForObject(
                "insert into buildings (name, type, lat, lng) values ('새 건물', 'CAMPUS', 37.5, 127.0) returning id",
                Long.class);
        try {
            assertThat(newId).isGreaterThan(107L);
        } finally {
            jdbc.update("delete from buildings where id = ?", newId);
        }
    }

    @Test
    void seeder_failsOnUnknownBuildingReference() {
        FixtureSeeder broken = new FixtureSeeder(jdbc, objectMapper, resourceLoader, txManager, users, clockBean,
                true, "classpath:test-fixtures-broken/");
        jdbc.execute("truncate table departments restart identity cascade");
        try {
            assertThatThrownBy(broken::seedIfEmpty)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("999");
            assertThat(count("departments")).isZero();
        } finally {
            seeder.seedIfEmpty();
        }
    }

    @Test
    void seeder_failsOnMissingCoordinates() {
        FixtureSeeder broken = new FixtureSeeder(jdbc, objectMapper, resourceLoader, txManager, users, clockBean,
                true, "classpath:test-fixtures-broken-buildings/");
        jdbc.execute("truncate table departments, buildings restart identity cascade");
        try {
            assertThatThrownBy(broken::seedIfEmpty)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("lat");
            assertThat(count("buildings")).isZero();
        } finally {
            seeder.seedIfEmpty();
        }
    }

    @Test
    void demoUser_createsOnceAndReusesByNickname() {
        long first = seeder.demoUser("데모작성자");
        long second = seeder.demoUser("데모작성자");

        assertThat(second).isEqualTo(first);
        assertThat(users.findById(first)).hasValueSatisfying(u -> {
            assertThat(u.getNickname()).isEqualTo("데모작성자");
            assertThat(u.getEmail()).doesNotEndWith("@hanyang.ac.kr");
        });
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
}
