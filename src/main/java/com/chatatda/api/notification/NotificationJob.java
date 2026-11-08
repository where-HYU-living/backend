package com.chatatda.api.notification;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 알림 만들기 (계약 5-10): 구독한 단지 공고 일정 D-7·D-1·당일, 내 집 계약 만료 daysBefore 일치.
 * 같은 알림은 dedupe_key 로 한 번만 만든다 → 서버가 뜰 때 한 번 더 돌려도 안전하다.
 */
@Component
public class NotificationJob {

    private static final Logger log = LoggerFactory.getLogger(NotificationJob.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int[] DDAY = {7, 1, 0};
    private static final Map<String, String> EVENT = Map.of("NOTICE", "모집공고", "APPLY_START", "접수 시작",
            "APPLY_END", "접수 마감", "DOCS_DUE", "서류 마감", "RESULT", "발표", "CONTRACT", "계약", "MOVE_IN", "입주");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public NotificationJob(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onStart() {
        try {
            run();
        } catch (RuntimeException e) {
            log.warn("알림 만들기 실패(기동 시)", e);
        }
    }

    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void daily() {
        run();
    }

    /** @return 새로 만든 알림 수 */
    public int run() {
        LocalDate today = LocalDate.now(clock.withZone(KST));
        int made = 0;
        for (int d : DDAY) {
            LocalDate day = today.plusDays(d);
            made += jdbc.query("""
                    select s.user_id, h.id as housing_id, h.name, e.type, e.event_date
                    from housing_subscriptions s
                    join housing h on h.id = s.housing_id
                    join announcements a on a.housing_id = h.id
                    join announcement_events e on e.announcement_id = a.id
                    where e.event_date = ?""", rs -> {
                int n = 0;
                while (rs.next()) {
                    String label = EVENT.getOrDefault(rs.getString("type"), rs.getString("type"));
                    String when = d == 0 ? "오늘" : "D-" + d;
                    n += insert(rs.getLong("user_id"), "HOUSING_DDAY",
                            rs.getString("name") + " " + label + " " + when,
                            rs.getDate("event_date").toLocalDate() + " " + label + " 일정이에요.",
                            "HOUSING", rs.getLong("housing_id"),
                            "dday:" + rs.getLong("housing_id") + ":" + rs.getString("type") + ":" + rs.getDate("event_date") + ":" + d);
                }
                return n;
            }, Date.valueOf(day));
        }
        made += jdbc.query("""
                select a.user_id, a.days_before, h.contract_end, h.address
                from home_alerts a join my_homes h on h.user_id = a.user_id
                where a.enabled and h.contract_end - a.days_before = ?""", rs -> {
            int n = 0;
            while (rs.next()) {
                int days = rs.getInt("days_before");
                n += insert(rs.getLong("user_id"), "CONTRACT_EXPIRY", "계약 만료 " + days + "일 전",
                        rs.getString("address") + " 계약이 " + rs.getDate("contract_end") + " 에 끝나요.", null, null,
                        "expiry:" + rs.getDate("contract_end") + ":" + days);
            }
            return n;
        }, Date.valueOf(today));
        if (made > 0) {
            log.info("알림 {}건 만듦", made);
        }
        return made;
    }

    private int insert(long userId, String type, String title, String body, String targetType, Long targetId, String key) {
        return jdbc.update("""
                insert into notifications (user_id, type, title, body, target_type, target_id, dedupe_key, created_at)
                values (?, ?, ?, ?, ?, ?, ?, ?) on conflict (user_id, dedupe_key) do nothing""",
                userId, type, title, body, targetType, targetId, key, Timestamp.from(clock.instant()));
    }
}
