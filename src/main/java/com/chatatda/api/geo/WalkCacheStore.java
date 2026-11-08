package com.chatatda.api.geo;

import jakarta.annotation.PreDestroy;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 도보 시간 DB 캐시 (walk_cache).
 * 저장은 요청이 이미 DB 연결을 쥔 트랜잭션 안이면 따로 도는 한 줄짜리 스레드에 맡긴다.
 * (예전엔 REQUIRES_NEW 로 연결을 하나 더 빌렸는데, 캐시에 없는 도보 시간 요청이 연결 수만큼 동시에 오면
 *  모두 두 번째 연결을 기다리며 서로 막혀 API 전체가 30초 멈췄다)
 */
@Component
public class WalkCacheStore {

    private static final Logger log = LoggerFactory.getLogger(WalkCacheStore.class);

    private final JdbcTemplate jdbc;
    /** 밀리면 오래된 것부터 버린다 (캐시라 잃어도 다음에 다시 계산할 뿐) */
    private final ThreadPoolExecutor writer = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(500), r -> {
                Thread t = new Thread(r, "walk-cache-writer");
                t.setDaemon(true);
                return t;
            }, new ThreadPoolExecutor.DiscardOldestPolicy());

    public WalkCacheStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Integer> findMinutes(WalkKey key, Instant notBefore) {
        List<Integer> found = jdbc.queryForList("""
                        select minutes from walk_cache
                        where lat4 = ? and lng4 = ? and building_id = ? and fetched_at >= ?""",
                Integer.class, key.lat4(), key.lng4(), key.buildingId(), Timestamp.from(notBefore));
        return found.stream().findFirst();
    }

    void save(WalkKey key, int minutes, int distanceM, Instant fetchedAt) {
        Runnable write = () -> {
            try {
                jdbc.update("""
                                insert into walk_cache (lat4, lng4, building_id, minutes, distance_m, fetched_at)
                                values (?, ?, ?, ?, ?, ?)
                                on conflict (lat4, lng4, building_id)
                                do update set minutes = excluded.minutes, distance_m = excluded.distance_m, fetched_at = excluded.fetched_at""",
                        key.lat4(), key.lng4(), key.buildingId(), minutes, distanceM, Timestamp.from(fetchedAt));
            } catch (RuntimeException e) { // 저장 실패(연결 부족 포함)는 응답에도, TMAP 실패 횟수에도 넣지 않는다
                log.warn("도보 시간 캐시 저장 실패 (응답에는 영향 없음): {}", e.getMessage());
            }
        };
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            writer.execute(write);
        } else {
            write.run(); // 트랜잭션 밖이면 쥔 연결이 없으니 바로 (자동 커밋 한 줄)
        }
    }

    @PreDestroy
    void close() throws InterruptedException {
        writer.shutdown();
        writer.awaitTermination(3, TimeUnit.SECONDS);
    }
}
