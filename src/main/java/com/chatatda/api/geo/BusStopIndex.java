package com.chatatda.api.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class BusStopIndex {

    private static final Logger log = LoggerFactory.getLogger(BusStopIndex.class);
    private static final int MAX_RESULTS = 15;

    private record Stop(String name, double lat, double lng) {
    }

    private final List<Stop> stops;

    public BusStopIndex(ObjectMapper objectMapper, ResourceLoader resourceLoader,
                        @Value("${app.bus-stops.location}") String location) {
        this.stops = load(objectMapper, resourceLoader.getResource(location), location);
    }

    public List<Place> near(double lat, double lng, int radius) {
        return stops.stream()
                .map(s -> new Place(NearbyCategory.BUS_STOP, s.name(), s.lat(), s.lng(), GeoMath.distanceM(lat, lng, s.lat(), s.lng())))
                .filter(p -> p.distanceM() <= radius)
                .sorted(Comparator.comparingInt(Place::distanceM))
                .limit(MAX_RESULTS)
                .toList();
    }

    private static List<Stop> load(ObjectMapper objectMapper, Resource resource, String location) {
        if (!resource.exists()) {
            log.warn("버스정류장 파일이 없어서 비워 둠 ({})", location);
            return List.of();
        }
        try (InputStream in = resource.getInputStream()) {
            List<Stop> loaded = new ArrayList<>();
            for (JsonNode s : objectMapper.readTree(in).path("stops")) {
                loaded.add(new Stop(s.path("name").asText(), s.path("lat").asDouble(), s.path("lng").asDouble()));
            }
            log.info("버스정류장 {}곳 읽음 ({})", loaded.size(), location);
            return List.copyOf(loaded);
        } catch (IOException e) {
            throw new IllegalStateException("버스정류장 파일을 읽지 못했어요: " + location, e);
        }
    }
}
