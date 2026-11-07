package com.chatatda.api.housing;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnnouncementStatusPolicyTest {

    private final AnnouncementStatusPolicy policy = new AnnouncementStatusPolicy();

    private static final Map<EventType, LocalDate> EVENTS = Map.of(
            EventType.NOTICE, LocalDate.of(2026, 11, 5),
            EventType.APPLY_START, LocalDate.of(2026, 11, 10),
            EventType.APPLY_END, LocalDate.of(2026, 11, 12),
            EventType.RESULT, LocalDate.of(2026, 11, 16));

    @Test
    void upcoming_open_closed_announced() {
        assertThat(policy.statusOf(EVENTS, false, LocalDate.of(2026, 11, 9))).isEqualTo(AnnouncementStatus.UPCOMING);
        assertThat(policy.statusOf(EVENTS, false, LocalDate.of(2026, 11, 10))).isEqualTo(AnnouncementStatus.OPEN);
        assertThat(policy.statusOf(EVENTS, false, LocalDate.of(2026, 11, 12))).isEqualTo(AnnouncementStatus.OPEN);
        assertThat(policy.statusOf(EVENTS, false, LocalDate.of(2026, 11, 13))).isEqualTo(AnnouncementStatus.CLOSED);
        assertThat(policy.statusOf(EVENTS, false, LocalDate.of(2026, 11, 16))).isEqualTo(AnnouncementStatus.ANNOUNCED);
    }

    @Test
    void additionalWithinApply() {
        assertThat(policy.statusOf(EVENTS, true, LocalDate.of(2026, 11, 11))).isEqualTo(AnnouncementStatus.ADDITIONAL);
        assertThat(policy.statusOf(EVENTS, true, LocalDate.of(2026, 11, 9))).isEqualTo(AnnouncementStatus.UPCOMING);
    }

    @Test
    void noApplySchedule_closed() {
        assertThat(policy.statusOf(Map.of(), false, LocalDate.of(2026, 11, 9))).isEqualTo(AnnouncementStatus.CLOSED);
    }

    @Test
    void noResultDate_closedAfterApply() {
        Map<EventType, LocalDate> events = Map.of(
                EventType.APPLY_START, LocalDate.of(2026, 11, 10),
                EventType.APPLY_END, LocalDate.of(2026, 11, 12));
        assertThat(policy.statusOf(events, false, LocalDate.of(2027, 1, 1))).isEqualTo(AnnouncementStatus.CLOSED);
    }
}
