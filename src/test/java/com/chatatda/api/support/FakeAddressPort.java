package com.chatatda.api.support;

import com.chatatda.api.geo.AddressPort;
import com.chatatda.api.geo.AddressResult;
import com.chatatda.api.geo.ExternalApiException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class FakeAddressPort implements AddressPort {

    private volatile List<AddressResult> results = List.of();
    private final Map<String, List<AddressResult>> byQuery = new ConcurrentHashMap<>();
    private volatile boolean failing;

    @Override
    public List<AddressResult> search(String query) {
        if (failing) {
            throw new ExternalApiException("카카오 장애 (테스트)");
        }
        return byQuery.getOrDefault(query, results);
    }

    public void setResults(List<AddressResult> results) {
        this.results = results;
    }

    public void setResultsFor(String query, List<AddressResult> results) {
        byQuery.put(query, results);
    }

    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    public void reset() {
        results = List.of();
        byQuery.clear();
        failing = false;
    }
}
