package com.chatatda.api.geo;

public interface WalkPort {

    WalkResult route(double fromLat, double fromLng, double toLat, double toLng);
}
