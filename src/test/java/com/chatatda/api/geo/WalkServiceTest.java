package com.chatatda.api.geo;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatatda.api.catalog.BuildingRepository;
import com.chatatda.api.support.IntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class WalkServiceTest extends IntegrationTest {

    @Autowired
    WalkService walkService;

    @Autowired
    WalkCacheStore store;

    @Autowired
    BuildingRepository buildings;

    private WalkService restarted() {
        return new WalkService(walkPort, buildings, store, clock);
    }

    @Test
    void walkService_caches() {
        assertThat(walkService.minutesOrNull(37.5580, 127.0410, 101L)).isEqualTo(7);
        assertThat(walkService.minutesOrNull(37.55801, 127.04101, 101L)).isEqualTo(7);
        assertThat(walkPort.calls()).isEqualTo(1);
    }

    @Test
    void noBuilding_orUnknownBuilding_isNullWithoutCall() {
        assertThat(walkService.minutesOrNull(37.5580, 127.0410, null)).isNull();
        assertThat(walkService.minutesOrNull(37.5580, 127.0410, 999L)).isNull();
        assertThat(walkPort.calls()).isZero();
    }

    @Test
    void cacheSurvivesRestart() {
        walkService.minutesOrNull(37.5580, 127.0410, 101L);

        assertThat(restarted().minutesOrNull(37.5580, 127.0410, 101L)).isEqualTo(7);
        assertThat(walkPort.calls()).isEqualTo(1);
    }

    @Test
    void storedCacheExpiresAfter30Days() {
        walkService.minutesOrNull(37.5580, 127.0410, 101L);
        clock.advance(Duration.ofDays(31));

        restarted().minutesOrNull(37.5580, 127.0410, 101L);

        assertThat(walkPort.calls()).isEqualTo(2);
    }

    @Test
    void failure_isNotCached() {
        walkPort.setFailing(true);
        assertThat(walkService.minutesOrNull(37.5580, 127.0410, 101L)).isNull();

        walkPort.setFailing(false);
        assertThat(walkService.minutesOrNull(37.5580, 127.0410, 101L)).isEqualTo(7);
        assertThat(walkPort.calls()).isEqualTo(2);
    }

    @Test
    void repeatedFailures_pauseCallsForOneMinute() {
        walkPort.setFailing(true);
        for (int i = 0; i < 3; i++) {
            assertThat(walkService.minutesOrNull(37.5580 + i * 0.001, 127.0410, 101L)).isNull();
        }
        assertThat(walkPort.calls()).isEqualTo(3);

        assertThat(walkService.minutesOrNull(37.5700, 127.0410, 101L)).isNull();
        assertThat(walkPort.calls()).as("paused, no call").isEqualTo(3);

        walkPort.setFailing(false);
        clock.advance(Duration.ofSeconds(61));
        assertThat(walkService.minutesOrNull(37.5700, 127.0410, 101L)).isEqualTo(7);
        assertThat(walkPort.calls()).isEqualTo(4);
    }
}
