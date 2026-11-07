package com.chatatda.api.housing;

import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class AnnouncementStatusPolicy {

    public AnnouncementStatus statusOf(Map<EventType, LocalDate> events, boolean additional, LocalDate today) {
        LocalDate applyStart = events.get(EventType.APPLY_START);
        LocalDate applyEnd = events.get(EventType.APPLY_END);
        if (applyStart == null || applyEnd == null) {
            return AnnouncementStatus.CLOSED;
        }
        if (today.isBefore(applyStart)) {
            return AnnouncementStatus.UPCOMING;
        }
        if (!today.isAfter(applyEnd)) {
            return additional ? AnnouncementStatus.ADDITIONAL : AnnouncementStatus.OPEN;
        }
        LocalDate result = events.get(EventType.RESULT);
        if (result != null && !today.isBefore(result)) {
            return AnnouncementStatus.ANNOUNCED;
        }
        return AnnouncementStatus.CLOSED;
    }
}
