package com.chatatda.api.geo;

record WalkKey(int lat4, int lng4, long buildingId) {

    static WalkKey of(double lat, double lng, long buildingId) {
        return new WalkKey((int) Math.round(lat * 10_000), (int) Math.round(lng * 10_000), buildingId);
    }
}
