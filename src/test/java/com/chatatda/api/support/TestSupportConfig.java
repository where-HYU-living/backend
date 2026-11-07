package com.chatatda.api.support;

import java.time.Instant;
import java.time.ZoneId;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration(proxyBeanMethods = false)
public class TestSupportConfig {

    @Bean
    @Primary
    MutableClock testClock() {
        return new MutableClock(Instant.parse("2026-11-07T00:00:00Z"), ZoneId.of("Asia/Seoul"));
    }

    @Bean
    @Primary
    FakeMailPort fakeMailPort() {
        return new FakeMailPort();
    }

    @Bean
    @Primary
    FakeWalkPort fakeWalkPort() {
        return new FakeWalkPort();
    }

    @Bean
    @Primary
    FakePlacePort fakePlacePort() {
        return new FakePlacePort();
    }

    @Bean
    @Primary
    FakeAddressPort fakeAddressPort() {
        return new FakeAddressPort();
    }

    @Bean
    @Primary
    FakeSocoClient fakeSocoClient() {
        return new FakeSocoClient();
    }
}
