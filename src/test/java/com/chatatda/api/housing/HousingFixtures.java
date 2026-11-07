package com.chatatda.api.housing;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;

final class HousingFixtures {

    private HousingFixtures() {
    }

    static long housing(JdbcTemplate jdbc, String name, double lat, double lng) {
        return jdbc.queryForObject("""
                insert into housing (name, address, dong, lat, lng, contact, updated_at)
                values (?, ?, '마장동', ?, ?, '02-0000-0000', ?) returning id""", Long.class,
                name, "서울 성동구 " + name, lat, lng, Timestamp.from(Instant.parse("2026-11-07T00:00:00Z")));
    }

    static long unit(JdbcTemplate jdbc, long housingId, String name, double area, long deposit, long rent, Long mgmtFee) {
        return jdbc.queryForObject("""
                insert into housing_unit_types (housing_id, name, room_kind, exclusive_area_m2, deposit, rent, mgmt_fee)
                values (?, ?, '원룸', ?, ?, ?, ?) returning id""", Long.class,
                housingId, name, area, deposit, rent, mgmtFee);
    }

    static long announcement(JdbcTemplate jdbc, long housingId, String title, boolean additional,
                             Map<EventType, LocalDate> events) {
        long id = jdbc.queryForObject("""
                insert into announcements (housing_id, title, additional, posted_on) values (?, ?, ?, ?) returning id""",
                Long.class, housingId, title, additional, LocalDate.of(2026, 11, 1));
        events.forEach((type, date) -> jdbc.update(
                "insert into announcement_events (announcement_id, type, event_date) values (?, ?, ?)", id, type.name(), date));
        return id;
    }
}
