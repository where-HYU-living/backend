package com.chatatda.api.geo;

public record Place(NearbyCategory category, String name, double lat, double lng, int distanceM) {
}
