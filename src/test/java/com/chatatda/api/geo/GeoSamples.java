package com.chatatda.api.geo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

final class GeoSamples {

    private GeoSamples() {
    }

    static String read(String name) {
        try (InputStream in = GeoSamples.class.getResourceAsStream("/geo/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
