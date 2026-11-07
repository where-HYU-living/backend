package com.chatatda.api.geo;

import com.chatatda.api.catalog.Building;
import com.chatatda.api.catalog.BuildingRepository;
import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.ErrorCode;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class WalkService {

    private static final Logger log = LoggerFactory.getLogger(WalkService.class);

    private static final int FAILURES_TO_PAUSE = 3;
    private static final Duration PAUSE = Duration.ofMinutes(1);
    private static final Duration MEMORY_TTL = Duration.ofHours(24);
    private static final Duration STORED_TTL = Duration.ofDays(30);

    public record WalkDto(int minutes, int distanceM, List<double[]> path, Long gateId) {
    }

    private final WalkPort port;
    private final BuildingRepository buildings;
    private final WalkCacheStore store;
    private final Clock clock;

    private final Cache<WalkKey, Integer> minutes =
            Caffeine.newBuilder().expireAfterWrite(MEMORY_TTL).maximumSize(10_000).build();
    private final Cache<WalkKey, WalkResult> routes =
            Caffeine.newBuilder().expireAfterWrite(MEMORY_TTL).maximumSize(1_000).build();
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private volatile Instant pausedUntil;

    public WalkService(WalkPort port, BuildingRepository buildings, WalkCacheStore store, Clock clock) {
        this.port = port;
        this.buildings = buildings;
        this.store = store;
        this.clock = clock;
    }

    public Integer minutesOrNull(double lat, double lng, Long buildingId) {
        if (buildingId == null) {
            return null;
        }
        WalkKey key = WalkKey.of(lat, lng, buildingId);
        Integer cached = minutes.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        Optional<Integer> stored = store.findMinutes(key, clock.instant().minus(STORED_TTL));
        if (stored.isPresent()) {
            minutes.put(key, stored.get());
            return stored.get();
        }
        Building building = buildings.findById(buildingId).orElse(null);
        if (building == null) {
            return null;
        }
        try {
            return fetch(key, lat, lng, building).minutes();
        } catch (ExternalApiException e) {
            return null;
        }
    }

    public WalkDto route(double lat, double lng, long buildingId) {
        Building building = buildings.findById(buildingId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "건물을 찾을 수 없어요."));
        WalkKey key = WalkKey.of(lat, lng, buildingId);
        WalkResult result = routes.getIfPresent(key);
        if (result == null) {
            try {
                result = fetch(key, lat, lng, building);
            } catch (ExternalApiException e) {
                throw new ApiException(ErrorCode.UPSTREAM_FAILED, "도보 경로를 가져오지 못했어요. 잠시 뒤 다시 시도해 주세요.");
            }
        }
        return new WalkDto(result.minutes(), result.distanceM(), result.path(), null);
    }

    public void clearCaches() {
        minutes.invalidateAll();
        routes.invalidateAll();
        consecutiveFailures.set(0);
        pausedUntil = null;
    }

    private WalkResult fetch(WalkKey key, double lat, double lng, Building building) {
        Instant now = clock.instant();
        Instant paused = pausedUntil;
        if (paused != null && now.isBefore(paused)) {
            throw new ExternalApiException("TMAP 호출을 잠시 멈춘 상태예요.");
        }
        try {
            WalkResult result = port.route(lat, lng, building.getLat(), building.getLng());
            consecutiveFailures.set(0);
            pausedUntil = null;
            minutes.put(key, result.minutes());
            routes.put(key, result);
            store.save(key, result.minutes(), result.distanceM(), now);
            return result;
        } catch (RuntimeException e) {
            if (consecutiveFailures.incrementAndGet() >= FAILURES_TO_PAUSE) {
                pausedUntil = now.plus(PAUSE);
                consecutiveFailures.set(0);
                log.warn("TMAP 연속 실패로 {}초 동안 호출을 멈춤", PAUSE.toSeconds());
            }
            throw e instanceof ExternalApiException ex ? ex : new ExternalApiException("TMAP 호출 실패", e);
        }
    }
}
