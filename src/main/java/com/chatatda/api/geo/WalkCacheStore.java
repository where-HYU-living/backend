package com.chatatda.api.geo;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
public class WalkCacheStore {

    private static final Logger log = LoggerFactory.getLogger(WalkCacheStore.class);

    private final JdbcTemplate jdbc;
    private final TransactionTemplate ownTransaction;

    public WalkCacheStore(JdbcTemplate jdbc, PlatformTransactionManager txManager) {
        this.jdbc = jdbc;
        this.ownTransaction = new TransactionTemplate(txManager);
        this.ownTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    Optional<Integer> findMinutes(WalkKey key, Instant notBefore) {
        List<Integer> found = jdbc.queryForList("""
                        select minutes from walk_cache
                        where lat4 = ? and lng4 = ? and building_id = ? and fetched_at >= ?""",
                Integer.class, key.lat4(), key.lng4(), key.buildingId(), Timestamp.from(notBefore));
        return found.stream().findFirst();
    }

    void save(WalkKey key, int minutes, int distanceM, Instant fetchedAt) {
        try {
            ownTransaction.executeWithoutResult(status -> jdbc.update("""
                            insert into walk_cache (lat4, lng4, building_id, minutes, distance_m, fetched_at)
                            values (?, ?, ?, ?, ?, ?)
                            on conflict (lat4, lng4, building_id)
                            do update set minutes = excluded.minutes, distance_m = excluded.distance_m, fetched_at = excluded.fetched_at""",
                    key.lat4(), key.lng4(), key.buildingId(), minutes, distanceM, Timestamp.from(fetchedAt)));
        } catch (DataAccessException e) {
            log.warn("도보 시간 캐시 저장 실패 (응답에는 영향 없음): {}", e.getMessage());
        }
    }
}
