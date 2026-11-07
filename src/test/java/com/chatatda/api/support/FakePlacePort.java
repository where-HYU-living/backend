package com.chatatda.api.support;

import com.chatatda.api.geo.ExternalApiException;
import com.chatatda.api.geo.NearbyCategory;
import com.chatatda.api.geo.Place;
import com.chatatda.api.geo.PlacePort;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class FakePlacePort implements PlacePort {

    private final Map<NearbyCategory, List<Place>> results = new EnumMap<>(NearbyCategory.class);
    private volatile boolean failing;
    private volatile Integer lastRadius;

    @Override
    public List<Place> near(double lat, double lng, int radius, NearbyCategory category) {
        lastRadius = radius;
        if (failing) {
            throw new ExternalApiException("카카오 장애 (테스트)");
        }
        return results.getOrDefault(category, List.of());
    }

    public void setResult(NearbyCategory category, List<Place> places) {
        results.put(category, places);
    }

    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    public Integer lastRadius() {
        return lastRadius;
    }

    public void reset() {
        results.clear();
        failing = false;
        lastRadius = null;
    }
}
