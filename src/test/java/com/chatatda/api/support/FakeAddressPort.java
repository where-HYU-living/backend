package com.chatatda.api.support;

import com.chatatda.api.geo.AddressPort;
import com.chatatda.api.geo.AddressResult;
import com.chatatda.api.geo.ExternalApiException;
import java.util.List;

public class FakeAddressPort implements AddressPort {

    private volatile List<AddressResult> results = List.of();
    private volatile boolean failing;

    @Override
    public List<AddressResult> search(String query) {
        if (failing) {
            throw new ExternalApiException("카카오 장애 (테스트)");
        }
        return results;
    }

    public void setResults(List<AddressResult> results) {
        this.results = results;
    }

    public void setFailing(boolean failing) {
        this.failing = failing;
    }

    public void reset() {
        results = List.of();
        failing = false;
    }
}
