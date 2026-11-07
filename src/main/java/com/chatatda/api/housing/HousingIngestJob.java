package com.chatatda.api.housing;

import com.chatatda.api.geo.AddressPort;
import com.chatatda.api.geo.AddressResult;
import com.chatatda.api.geo.ExternalApiException;
import com.chatatda.api.housing.soco.ScheduleParser;
import com.chatatda.api.housing.soco.SocoApiException;
import com.chatatda.api.housing.soco.SocoClient;
import com.chatatda.api.housing.soco.SocoHome;
import com.chatatda.api.housing.soco.SocoHomeDetail;
import com.chatatda.api.housing.soco.SocoNotice;
import com.chatatda.api.housing.soco.SocoParser;
import com.chatatda.api.housing.soco.SocoUnit;
import com.chatatda.api.realdeal.DongAdjacency;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class HousingIngestJob {

    private static final Logger log = LoggerFactory.getLogger(HousingIngestJob.class);
    private static final Pattern AREA_IN_NAME = Pattern.compile("(\\d+(?:\\.\\d+)?)");
    private static final Pattern DONG_IN_TEXT = Pattern.compile("([가-힣]+\\d*동)(?=[\\s,)]|\\d|$)");
    private static final int MAX_NOTICE_PAGES = 100;

    public record Report(int homes, int announcements, int unlabeledImages, List<String> failures) {
    }

    /**
     * floorplanId: 이 이미지를 3D 로 옮긴 평면도(floorplans 테이블) id. use 가 "listing" 이면 매물 데모로 넘긴 평면도라서
     * 주택형에는 연결하지 않는다.
     */
    record Label(String sha256, String unitType, Double exclusiveAreaM2, Long floorplanId, String use) {
        boolean linksToUnitType() {
            return floorplanId != null && !"listing".equalsIgnoreCase(use);
        }
    }

    private final SocoClient client;
    private final SocoParser parser;
    private final ScheduleParser scheduleParser;
    private final AddressPort addresses;
    private final Set<String> targetDongs;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;
    private final Clock clock;
    private final boolean enabled;
    private final Set<String> districts;
    private final int noticeMonths;
    private final long intervalMs;
    private final String labelsLocation;

    public HousingIngestJob(SocoClient client, SocoParser parser, ScheduleParser scheduleParser, AddressPort addresses,
                            DongAdjacency dongAdjacency, JdbcTemplate jdbc, PlatformTransactionManager txManager,
                            ObjectMapper objectMapper, ResourceLoader resourceLoader, Clock clock,
                            @Value("${app.housing-ingest.enabled}") boolean enabled,
                            @Value("${app.housing-ingest.districts}") List<String> districts,
                            @Value("${app.housing-ingest.notice-months}") int noticeMonths,
                            @Value("${app.housing-ingest.request-interval-ms}") long intervalMs,
                            @Value("${app.housing-ingest.labels-location}") String labelsLocation) {
        this.client = client;
        this.parser = parser;
        this.scheduleParser = scheduleParser;
        this.addresses = addresses;
        this.targetDongs = dongAdjacency.dongs();
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txManager);
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.clock = clock;
        this.enabled = enabled;
        this.districts = Set.copyOf(districts);
        this.noticeMonths = noticeMonths;
        this.intervalMs = intervalMs;
        this.labelsLocation = labelsLocation;
    }

    @Order(3)
    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        Integer count = jdbc.queryForObject("select count(*) from housing", Integer.class);
        if (enabled && (count == null || count == 0)) {
            logReport(run(LocalDate.now(clock)));
        }
    }

    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
    public void daily() {
        if (enabled) {
            logReport(run(LocalDate.now(clock)));
        }
    }

    public Report run(LocalDate today) {
        List<String> failures = new ArrayList<>();
        Map<String, Label> labels = loadLabels();
        int homes = 0;
        int unlabeled = 0;
        List<SocoHome> list;
        try {
            list = parser.homes(client.homeList());
        } catch (SocoApiException e) {
            failures.add("단지 목록: " + e.getMessage());
            return new Report(0, 0, 0, failures);
        }
        for (SocoHome home : list) {
            if (home.district() == null || !districts.contains(home.district())) {
                continue;
            }
            try {
                AddressResult address = lookup(home.address());
                String dong = address != null && address.dong() != null ? address.dong() : dongFromText(home.address());
                if (dong == null) {
                    failures.add("단지 " + home.homeCode() + ": 주소에서 동을 찾지 못했어요 (" + home.address() + ")");
                    continue;
                }
                if (!targetDongs.contains(dong)) {
                    continue;
                }
                pause();
                SocoHomeDetail detail = parser.detail(client.homeDetail(home.homeCode()));
                List<ImageRow> images = images(detail, labels);
                unlabeled += (int) images.stream().filter(i -> i.label() == null).count();
                tx.executeWithoutResult(s -> saveHome(home, dong, address, detail, images));
                homes++;
            } catch (RuntimeException e) {
                failures.add("단지 " + home.homeCode() + ": " + e.getMessage());
            }
        }
        int announcements = 0;
        try {
            announcements = ingestNotices(today, failures);
        } catch (SocoApiException e) {
            failures.add("공고 목록: " + e.getMessage());
        }
        return new Report(homes, announcements, unlabeled, failures);
    }

    private AddressResult lookup(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        try {
            List<AddressResult> results = addresses.search(address);
            return results.isEmpty() ? null : results.get(0);
        } catch (ExternalApiException e) {
            log.warn("카카오 주소 검색 실패, 주소 글자로 동을 찾음: {} ({})", address, e.getMessage());
            return null;
        }
    }

    static String dongFromText(String address) {
        if (address == null) {
            return null;
        }
        Matcher m = DONG_IN_TEXT.matcher(address);
        return m.find() ? m.group(1) : null;
    }

    record ImageRow(int position, String url, String sha256, Label label) {
    }

    private List<ImageRow> images(SocoHomeDetail detail, Map<String, Label> labels) {
        List<ImageRow> rows = new ArrayList<>();
        int position = 0;
        for (String url : detail.floorplanImageUrls()) {
            pause();
            String sha = sha256(client.image(url));
            rows.add(new ImageRow(position++, url, sha, labels.get(sha)));
        }
        return rows;
    }

    private void saveHome(SocoHome home, String dong, AddressResult address, SocoHomeDetail detail, List<ImageRow> images) {
        Double lat = detail.lat() != null ? detail.lat() : address == null ? null : address.lat();
        Double lng = detail.lng() != null ? detail.lng() : address == null ? null : address.lng();
        if (lat == null || lng == null) {
            throw new SocoApiException("좌표를 찾지 못했어요.");
        }
        Long housingId = jdbc.queryForObject("""
                insert into housing (soco_home_code, name, address, dong, lat, lng, contact, homepage_url, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, ?)
                on conflict (soco_home_code) do update set name = excluded.name, address = excluded.address,
                    dong = excluded.dong, lat = excluded.lat, lng = excluded.lng, contact = excluded.contact,
                    homepage_url = excluded.homepage_url, updated_at = excluded.updated_at
                returning id""", Long.class,
                home.homeCode(), home.name(), home.address(), dong, lat, lng, detail.phone(), detail.homepage(),
                Timestamp.from(clock.instant()));

        Map<String, Double> labelledArea = new HashMap<>();
        for (ImageRow image : images) {
            if (image.label() != null && image.label().exclusiveAreaM2() != null) {
                labelledArea.put(image.label().unitType(), image.label().exclusiveAreaM2());
            }
        }
        List<String> names = new ArrayList<>();
        for (SocoUnit unit : detail.units()) {
            names.add(unit.name());
            Double area = unit.exclusiveAreaM2() != null ? unit.exclusiveAreaM2()
                    : labelledArea.getOrDefault(unit.name(), areaFromName(unit.name()));
            jdbc.update("""
                    insert into housing_unit_types (housing_id, name, room_kind, exclusive_area_m2, deposit, rent, mgmt_fee)
                    values (?, ?, ?, ?, ?, ?, ?)
                    on conflict (housing_id, name) do update set room_kind = excluded.room_kind,
                        exclusive_area_m2 = excluded.exclusive_area_m2, deposit = excluded.deposit, rent = excluded.rent,
                        mgmt_fee = excluded.mgmt_fee""",
                    housingId, unit.name(), unit.roomKind(), area, unit.deposit(), unit.rent(), unit.mgmtFee());
        }
        if (!names.isEmpty()) {
            jdbc.update("delete from housing_unit_types where housing_id = ? and not (name = any (?))",
                    ps -> {
                        ps.setLong(1, housingId);
                        ps.setArray(2, ps.getConnection().createArrayOf("varchar", names.toArray()));
                    });
        }

        for (ImageRow image : images) {
            if (image.label() == null || !image.label().linksToUnitType()) {
                continue;
            }
            Long floorplanId = image.label().floorplanId();
            Integer exists = jdbc.queryForObject("select count(*) from floorplans where id = ?", Integer.class, floorplanId);
            if (exists == null || exists == 0) {
                log.warn("평면도 {} 가 floorplans 에 아직 없어서 연결 못 함: 단지 {} {}", floorplanId, home.name(), image.label().unitType());
                continue;
            }
            int linked = jdbc.update("update housing_unit_types set floorplan_id = ? where housing_id = ? and name = ?",
                    floorplanId, housingId, image.label().unitType());
            if (linked == 0) {
                log.warn("평면도 {} 를 붙일 주택형을 못 찾음: 단지 {} 주택형 '{}'", floorplanId, home.name(), image.label().unitType());
            }
        }

        jdbc.update("delete from housing_floorplan_images where housing_id = ?", housingId);
        for (ImageRow image : images) {
            jdbc.update("""
                    insert into housing_floorplan_images (housing_id, position, url, sha256, unit_type_name)
                    values (?, ?, ?, ?, ?) on conflict (url) do nothing""",
                    housingId, image.position(), image.url(), image.sha256(),
                    image.label() == null ? null : image.label().unitType());
            if (image.label() == null) {
                log.warn("평면도 연결표에 없는 이미지: 단지 {} {} (sha256 {})", home.name(), image.url(), image.sha256());
            }
        }
    }

    private int ingestNotices(LocalDate today, List<String> failures) {
        LocalDate cutoff = today.minusMonths(noticeMonths);
        Map<String, Long> housingByName = new HashMap<>();
        jdbc.query("select id, name from housing", rs -> {
            housingByName.put(normalize(rs.getString("name")), rs.getLong("id"));
        });
        Set<Long> known = new HashSet<>(jdbc.queryForList("select soco_board_id from announcements where soco_board_id is not null", Long.class));
        int saved = 0;
        boolean stop = false;
        for (int page = 1; page <= MAX_NOTICE_PAGES && !stop; page++) {
            pause();
            String json = client.notices(page);
            List<SocoNotice> notices = parser.notices(json);
            if (notices.isEmpty()) {
                break;
            }
            boolean allKnown = true;
            for (SocoNotice notice : notices) {
                if (notice.postedOn() == null || notice.postedOn().isBefore(cutoff)) {
                    stop = true;
                    continue;
                }
                if (!known.contains(notice.boardId())) {
                    allKnown = false;
                }
                Long housingId = match(notice.title(), housingByName);
                if (housingId == null) {
                    continue;
                }
                try {
                    tx.executeWithoutResult(s -> saveNotice(notice, housingId));
                    saved++;
                } catch (RuntimeException e) {
                    failures.add("공고 " + notice.boardId() + ": " + e.getMessage());
                }
            }
            if (allKnown && !known.isEmpty() || page >= parser.noticePages(json)) {
                stop = true;
            }
        }
        jdbc.update("delete from announcements where posted_on < ?", cutoff);
        return saved;
    }

    private void saveNotice(SocoNotice notice, long housingId) {
        Long announcementId = jdbc.queryForObject("""
                insert into announcements (soco_board_id, housing_id, title, additional, posted_on)
                values (?, ?, ?, ?, ?)
                on conflict (soco_board_id) do update set housing_id = excluded.housing_id, title = excluded.title,
                    additional = excluded.additional, posted_on = excluded.posted_on
                returning id""", Long.class,
                notice.boardId(), housingId, notice.title(), notice.title().contains("추가모집"), notice.postedOn());
        List<ScheduleParser.ParsedEvent> events = scheduleParser.parse(notice.bodyText());
        for (ScheduleParser.ParsedEvent event : events) {
            jdbc.update("""
                    insert into announcement_events (announcement_id, type, event_date) values (?, ?, ?)
                    on conflict (announcement_id, type) do update set event_date = excluded.event_date""",
                    announcementId, event.type().name(), event.date());
        }
        List<String> types = events.stream().map(e -> e.type().name()).toList();
        jdbc.update("delete from announcement_events where announcement_id = ? and not (type = any (?))",
                ps -> {
                    ps.setLong(1, announcementId);
                    ps.setArray(2, ps.getConnection().createArrayOf("varchar", types.toArray()));
                });
    }

    static Long match(String title, Map<String, Long> housingByName) {
        String normalizedTitle = normalize(title);
        Long found = null;
        int longest = 0;
        for (Map.Entry<String, Long> e : housingByName.entrySet()) {
            String name = e.getKey();
            if (!name.isEmpty() && normalizedTitle.contains(name) && name.length() > longest) {
                found = e.getValue();
                longest = name.length();
            }
        }
        return found;
    }

    static String normalize(String name) {
        if (name == null) {
            return "";
        }
        return name.replaceAll("\\([^)]*\\)", "")
                .replace("청년안심주택", "").replace("청년주택", "")
                .replaceAll("\\s+", "");
    }

    private static Double areaFromName(String name) {
        Matcher m = AREA_IN_NAME.matcher(name);
        return m.find() ? Double.valueOf(m.group(1)) : null;
    }

    private Map<String, Label> loadLabels() {
        Resource resource = resourceLoader.getResource(labelsLocation);
        if (!resource.exists()) {
            return Map.of();
        }
        try (InputStream in = resource.getInputStream()) {
            Map<String, Label> map = new HashMap<>();
            for (Label label : objectMapper.readValue(in, new TypeReference<List<Label>>() { })) {
                map.put(label.sha256(), label);
            }
            return map;
        } catch (IOException e) {
            throw new IllegalStateException("평면도 연결표 " + labelsLocation + " 를 읽지 못했어요.", e);
        }
    }

    private void pause() {
        if (intervalMs <= 0) {
            return;
        }
        try {
            Thread.sleep(intervalMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SocoApiException("수집이 중단됐어요.");
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void logReport(Report report) {
        log.info("청년안심주택 수집 끝: 단지 {}곳, 공고 {}건, 연결표 없는 평면도 {}장, 실패 {}건",
                report.homes(), report.announcements(), report.unlabeledImages(), report.failures().size());
        report.failures().forEach(f -> log.warn("수집 실패: {}", f));
    }
}
