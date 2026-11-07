package com.chatatda.api.geo;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NearbyService {

    private static final Logger log = LoggerFactory.getLogger(NearbyService.class);
    private static final List<NearbyCategory> ORDER =
            List.of(NearbyCategory.CONVENIENCE, NearbyCategory.LAUNDRY, NearbyCategory.BUS_STOP, NearbyCategory.SUBWAY);

    private record Key(int lat4, int lng4, int radius) {
    }

    private final PlacePort places;
    private final BusStopIndex busStops;
    private final Cache<Key, List<Place>> cache =
            Caffeine.newBuilder().expireAfterWrite(Duration.ofHours(1)).maximumSize(2_000).build();

    public NearbyService(PlacePort places, BusStopIndex busStops) {
        this.places = places;
        this.busStops = busStops;
    }

    public List<Place> near(double lat, double lng, int radius) {
        Key key = new Key((int) Math.round(lat * 10_000), (int) Math.round(lng * 10_000), radius);
        List<Place> cached = cache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        List<Place> all = new ArrayList<>();
        boolean complete = true;
        for (NearbyCategory category : ORDER) {
            try {
                all.addAll(category == NearbyCategory.BUS_STOP
                        ? busStops.near(lat, lng, radius)
                        : places.near(lat, lng, radius, category));
            } catch (RuntimeException e) {
                complete = false;
                log.warn("주변 시설 {} 조회 실패: {}", category, e.getMessage());
            }
        }
        List<Place> result = List.copyOf(all);
        if (complete) {
            cache.put(key, result);
        }
        return result;
    }

    public void clearCaches() {
        cache.invalidateAll();
    }
}
