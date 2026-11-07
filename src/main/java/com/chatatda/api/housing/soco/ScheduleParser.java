package com.chatatda.api.housing.soco;

import com.chatatda.api.housing.EventType;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** 공고 본문의 [공급일정] 블록("■항목 : ‘26. 07. 17. (금) ~ 07. 20.")을 일정으로 바꾼다. */
@Component
public class ScheduleParser {

    public record ParsedEvent(EventType type, LocalDate date) {
    }

    private static final String SECTION = "[공급일정]";
    private static final Pattern DATE = Pattern.compile("(?:[‘'’`](\\d{2})\\.\\s*)?(\\d{1,2})\\.\\s*(\\d{1,2})\\.");

    public List<ParsedEvent> parse(String text) {
        if (text == null) {
            return List.of();
        }
        int start = text.indexOf(SECTION);
        if (start < 0) {
            return List.of();
        }
        int end = text.indexOf('[', start + SECTION.length());
        String section = text.substring(start + SECTION.length(), end < 0 ? text.length() : end);

        Map<EventType, LocalDate> events = new EnumMap<>(EventType.class);
        Integer year = null;
        for (String item : section.split("■")) {
            int colon = item.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String label = item.substring(0, colon).replaceAll("\\s+", "");
            List<LocalDate> dates = new ArrayList<>();
            Matcher m = DATE.matcher(item.substring(colon + 1));
            while (m.find()) {
                if (m.group(1) != null) {
                    year = 2000 + Integer.parseInt(m.group(1));
                }
                if (year == null) {
                    continue;
                }
                LocalDate date = date(year, m.group(2), m.group(3));
                if (date == null) {
                    continue;
                }
                if (!dates.isEmpty() && date.isBefore(dates.get(dates.size() - 1))) {
                    year++;
                    date = date.plusYears(1);
                }
                dates.add(date);
            }
            if (dates.isEmpty()) {
                continue;
            }
            LocalDate first = dates.get(0);
            LocalDate last = dates.get(dates.size() - 1);
            if (label.contains("계약")) {
                events.putIfAbsent(EventType.CONTRACT, first);
            } else if (label.contains("입주")) {
                events.putIfAbsent(EventType.MOVE_IN, first);
            } else if (label.contains("발표")) {
                events.putIfAbsent(EventType.RESULT, first);
            } else if (label.contains("청약") || label.contains("접수")) {
                events.putIfAbsent(EventType.APPLY_START, first);
                events.putIfAbsent(EventType.APPLY_END, last);
            } else if (label.contains("모집공고")) {
                events.putIfAbsent(EventType.NOTICE, first);
            } else if (label.contains("서류") && label.contains("제출")) {
                events.putIfAbsent(EventType.DOCS_DUE, last);
            }
        }
        return events.entrySet().stream().map(e -> new ParsedEvent(e.getKey(), e.getValue())).toList();
    }

    private static LocalDate date(int year, String month, String day) {
        try {
            return LocalDate.of(year, Integer.parseInt(month), Integer.parseInt(day));
        } catch (DateTimeException e) {
            return null;
        }
    }
}
