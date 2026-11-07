package com.chatatda.api.support;

import com.chatatda.api.housing.soco.SocoApiException;
import com.chatatda.api.housing.soco.SocoClient;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class FakeSocoClient implements SocoClient {

    private static final Map<String, String> VIEWS = Map.of("20000602", "view-cheongajin.html");

    private volatile boolean failing;
    private final AtomicInteger detailCalls = new AtomicInteger();

    @Override
    public String homeList() {
        failIfNeeded();
        return resource("home-list.json");
    }

    @Override
    public String homeDetail(String homeCode) {
        failIfNeeded();
        detailCalls.incrementAndGet();
        return resource(VIEWS.getOrDefault(homeCode, "view-labom.html"));
    }

    @Override
    public String notices(int pageIndex) {
        failIfNeeded();
        return resource("notices.json");
    }

    @Override
    public byte[] image(String path) {
        failIfNeeded();
        return path.getBytes(StandardCharsets.UTF_8);
    }

    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    public int detailCalls() {
        return detailCalls.get();
    }

    public void reset() {
        failing = false;
        detailCalls.set(0);
    }

    private void failIfNeeded() {
        if (failing) {
            throw new SocoApiException("soco 장애 (테스트)");
        }
    }

    private static String resource(String name) {
        try (InputStream in = FakeSocoClient.class.getResourceAsStream("/soco/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
