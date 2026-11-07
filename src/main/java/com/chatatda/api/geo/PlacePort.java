package com.chatatda.api.geo;

import java.util.List;

public interface PlacePort {

    List<Place> near(double lat, double lng, int radius, NearbyCategory category);
}
