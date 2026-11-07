package com.chatatda.api.housing;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatatda.api.geo.AddressResult;
import com.chatatda.api.support.IntegrationTest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

class HousingIngestJobTest extends IntegrationTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 7);

    @Autowired
    private HousingIngestJob job;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private com.chatatda.api.floorplan.FloorPlanSeeder floorPlanSeeder;

    @BeforeEach
    void addresses() {
        dong("서울 성동구 홍익동 150 청아진 청년안심주택", "홍익동");
        dong("서울 성동구 마장동 339-2 왕십리역 라봄성동", "마장동");
        dong("서울 성동구 마장동 783-5 한영스테이", "마장동");
        dong("서울특별시 성동구 천호대로416 (용답동, 힐데스하임) 힐데스하임(U-삼진랜드)", "용답동");
        dong("서울 동대문구 전농동 127-359 퀸즈W청량리역", "전농동");
    }

    private void dong(String address, String dong) {
        addressPort.setResultsFor(address, List.of(new AddressResult(address, null, dong, 37.56, 127.04)));
    }

    private List<String> savedCodes() {
        return jdbc.queryForList("select soco_home_code from housing order by soco_home_code", String.class);
    }

    @Test
    void onlyTargetDongs() {
        dong("서울 동대문구 전농동 127-359 퀸즈W청량리역", "장안동");

        HousingIngestJob.Report report = job.run(TODAY);

        assertThat(report.failures()).isEmpty();
        assertThat(savedCodes()).containsExactly("20000358", "20000566", "20000580", "20000602");
        assertThat(socoClient.detailCalls()).isEqualTo(4);
    }

    @Test
    void savesUnitsCoordinatesAndContact() {
        job.run(TODAY);

        Map<String, Object> cheongajin = jdbc.queryForMap(
                "select id, name, lat, lng, contact, homepage_url, dong from housing where soco_home_code = '20000602'");
        assertThat(cheongajin).containsEntry("name", "왕십리역 청아진 청년주택")
                .containsEntry("lat", 37.5651154).containsEntry("lng", 127.0348084)
                .containsEntry("contact", "02-6273-0266").containsEntry("homepage_url", "https://caj266.com")
                .containsEntry("dong", "홍익동");
        assertThat(jdbc.queryForList("select name from housing_unit_types where housing_id = ? order by id",
                String.class, cheongajin.get("id"))).containsExactly("18타입(18.22)", "18A타입(18.22)");
    }

    @Test
    void floorplanLabels_fillAreaAndCountUnlabeled() {
        HousingIngestJob.Report report = job.run(TODAY);

        Double area = jdbc.queryForObject("""
                select u.exclusive_area_m2 from housing_unit_types u join housing h on h.id = u.housing_id
                where h.soco_home_code = '20000580' and u.name = '19A'""", Double.class);
        assertThat(area).isEqualTo(19.63);
        assertThat(jdbc.queryForObject("select unit_type_name from housing_floorplan_images where url like '%997b2f9c%fileSn=3'",
                String.class)).isEqualTo("19A");
        assertThat(report.unlabeledImages()).isPositive();
    }

    @Test
    void labelFloorplanId_linksUnitType_whenFloorplanExists() {
        job.run(TODAY);

        assertThat(jdbc.queryForObject("""
                select u.floorplan_id from housing_unit_types u join housing h on h.id = u.housing_id
                where h.soco_home_code = '20000580' and u.name = '19A'""", Long.class)).isEqualTo(1L);
    }

    @Test
    void labelFloorplanId_notLinked_whenFloorplanMissing() {
        jdbc.update("delete from floorplans where id = 1");
        try {
            job.run(TODAY);

            assertThat(jdbc.queryForObject("""
                    select u.floorplan_id from housing_unit_types u join housing h on h.id = u.housing_id
                    where h.soco_home_code = '20000580' and u.name = '19A'""", Long.class)).isNull();
        } finally {
            floorPlanSeeder.seed();
        }
    }

    @Test
    void announcements_recentYearOnly_withEvents() {
        job.run(TODAY);

        assertThat(jdbc.queryForList("select soco_board_id from announcements order by soco_board_id", Long.class))
                .containsExactly(6586L, 6673L);
        assertThat(jdbc.queryForObject("""
                select count(*) from announcement_events e join announcements a on a.id = e.announcement_id
                where a.soco_board_id = 6586""", Integer.class)).isEqualTo(7);
        assertThat(jdbc.queryForObject("select additional from announcements where soco_board_id = 6673", Boolean.class))
                .isTrue();
    }

    @Test
    void oldAnnouncements_deleted() {
        job.run(TODAY);
        long housingId = jdbc.queryForObject("select id from housing where soco_home_code = '20000580'", Long.class);
        jdbc.update("insert into announcements (soco_board_id, housing_id, title, additional, posted_on) values (1, ?, 'old', false, ?)",
                housingId, LocalDate.of(2025, 10, 1));

        job.run(TODAY);

        assertThat(jdbc.queryForObject("select count(*) from announcements where soco_board_id = 1", Integer.class)).isZero();
    }

    @Test
    void runTwice_noDuplicates() {
        job.run(TODAY);
        int housing = count("housing");
        int units = count("housing_unit_types");
        int anns = count("announcements");
        int events = count("announcement_events");
        int images = count("housing_floorplan_images");

        job.run(TODAY);

        assertThat(List.of(count("housing"), count("housing_unit_types"), count("announcements"),
                count("announcement_events"), count("housing_floorplan_images")))
                .containsExactly(housing, units, anns, events, images);
    }

    @Test
    void rerun_keepsIdsSubscriptionsAndFloorplanId() {
        job.run(TODAY);
        long housingId = jdbc.queryForObject("select id from housing where soco_home_code = '20000602'", Long.class);
        long eventId = jdbc.queryForObject("""
                select e.id from announcement_events e join announcements a on a.id = e.announcement_id
                where a.soco_board_id = 6586 and e.type = 'APPLY_END'""", Long.class);
        long userId = jdbc.queryForObject("insert into users (email, nickname, created_at) values ('a@hanyang.ac.kr', 'a', now()) returning id", Long.class);
        jdbc.update("insert into housing_subscriptions (user_id, housing_id, created_at) values (?, ?, ?)",
                userId, housingId, java.sql.Timestamp.from(Instant.parse("2026-11-07T00:00:00Z")));
        jdbc.update("update housing_unit_types set floorplan_id = 7 where housing_id = ? and name = '18타입(18.22)'", housingId);

        job.run(TODAY);

        assertThat(jdbc.queryForObject("select id from housing where soco_home_code = '20000602'", Long.class)).isEqualTo(housingId);
        assertThat(jdbc.queryForObject("""
                select e.id from announcement_events e join announcements a on a.id = e.announcement_id
                where a.soco_board_id = 6586 and e.type = 'APPLY_END'""", Long.class)).isEqualTo(eventId);
        assertThat(count("housing_subscriptions")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select floorplan_id from housing_unit_types where housing_id = ? and name = '18타입(18.22)'",
                Long.class, housingId)).isEqualTo(7L);
    }

    @Test
    void socoDown_keepsExistingData() {
        job.run(TODAY);
        int housing = count("housing");
        socoClient.setFailing(true);

        HousingIngestJob.Report report = job.run(TODAY);

        assertThat(report.failures()).isNotEmpty();
        assertThat(count("housing")).isEqualTo(housing);
        assertThat(count("announcements")).isEqualTo(2);
    }

    @Test
    void kakaoNoResult_dongFromAddressText() {
        addressPort.setResultsFor("서울 성동구 마장동 339-2 왕십리역 라봄성동", List.of());

        HousingIngestJob.Report report = job.run(TODAY);

        assertThat(report.failures()).isEmpty();
        assertThat(jdbc.queryForObject("select dong from housing where soco_home_code = '20000580'", String.class))
                .isEqualTo("마장동");
    }

    @Test
    void kakaoDown_dongFromAddressText() {
        addressPort.setFailing(true);

        HousingIngestJob.Report report = job.run(TODAY);

        assertThat(report.failures()).isEmpty();
        assertThat(savedCodes()).containsExactly("20000358", "20000566", "20000580", "20000600", "20000602");
        assertThat(jdbc.queryForObject("select dong from housing where soco_home_code = '20000358'", String.class))
                .isEqualTo("용답동");
    }

    @Test
    void dongFromText() {
        assertThat(HousingIngestJob.dongFromText("서울 성동구 마장동 339-2 왕십리역 라봄성동")).isEqualTo("마장동");
        assertThat(HousingIngestJob.dongFromText("서울특별시 성동구 천호대로416 (용답동, 힐데스하임) 힐데스하임(U-삼진랜드)"))
                .isEqualTo("용답동");
        assertThat(HousingIngestJob.dongFromText("서울 성동구 왕십리로 222")).isNull();
        assertThat(HousingIngestJob.dongFromText("서울 동대문구 전농동 127-359 퀸즈W청량리역")).isEqualTo("전농동");
    }

    private int count(String table) {
        return jdbc.queryForObject("select count(*) from " + table, Integer.class);
    }
}
