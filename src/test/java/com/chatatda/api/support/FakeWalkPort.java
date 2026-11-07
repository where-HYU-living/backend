package com.chatatda.api.support;

import com.chatatda.api.geo.ExternalApiException;
import com.chatatda.api.geo.WalkPort;
import com.chatatda.api.geo.WalkResult;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeWalkPort implements WalkPort {

    private static final WalkResult DEFAULT = new WalkResult(7, 500,
            List.of(new double[] {37.558, 127.041}, new double[] {37.5566, 127.0457}));

    private final AtomicInteger calls = new AtomicInteger();
    private volatile WalkResult result = DEFAULT;
    private volatile boolean failing;

    @Override
    public WalkResult route(double fromLat, double fromLng, double toLat, double toLng) {
        calls.incrementAndGet();
        if (failing) {
            throw new ExternalApiException("TMAP 장애 (테스트)");
        }
        return result;
    }

    public void setResult(WalkResult result) {
        this.result = result;
    }

    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    public int calls() {
        return calls.get();
    }

    public void reset() {
        calls.set(0);
        result = DEFAULT;
        failing = false;
    }
}
