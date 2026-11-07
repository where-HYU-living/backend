package com.chatatda.api.housing.soco;

import static org.assertj.core.api.Assertions.assertThat;

import com.chatatda.api.housing.EventType;
import java.time.LocalDate;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class ScheduleParserTest {

    private final ScheduleParser parser = new ScheduleParser();

    private Map<EventType, LocalDate> parse(String text) {
        return parser.parse(text).stream()
                .collect(Collectors.toMap(ScheduleParser.ParsedEvent::type, ScheduleParser.ParsedEvent::date));
    }

    @Test
    void cheongajin_fullSchedule() {
        Map<EventType, LocalDate> events = parse("[단지개요] ■단지명 : 청아진 [공급일정] ■모집공고 : ‘26. 07. 07. (화) "
                + "■청약신청 : ‘26. 07. 17. (금) 09:00 ~ 07. 20. (월) 23:00 ■서류심사 대상자 발표 : ‘26. 07. 21. (화) 17:00 "
                + "■당첨자 서류 사본 제출 : ‘26. 07. 21. (화) ~ 07. 29. (수) ■당첨자 세대 확인 : ‘26. 07. 24. (금) ~ 07. 25. (토) "
                + "■원본서류 제출 및 계약체결 : ‘26. 08. 06. (목) ~ 08. 09. (일) ■입주지정기간 : ‘26. 08. 17. (월) ~ 09. 25. (금) "
                + "[청약신청] ■ 청약신청 페이지 : https://caj266.com");

        assertThat(events).containsExactlyInAnyOrderEntriesOf(Map.of(
                EventType.NOTICE, LocalDate.of(2026, 7, 7),
                EventType.APPLY_START, LocalDate.of(2026, 7, 17),
                EventType.APPLY_END, LocalDate.of(2026, 7, 20),
                EventType.RESULT, LocalDate.of(2026, 7, 21),
                EventType.DOCS_DUE, LocalDate.of(2026, 7, 29),
                EventType.CONTRACT, LocalDate.of(2026, 8, 6),
                EventType.MOVE_IN, LocalDate.of(2026, 8, 17)));
    }

    @Test
    void labom_individualNoticeSkipped() {
        Map<EventType, LocalDate> events = parse("[공급일정] ■모집공고 : ‘26. 09. 17. (목) "
                + "■청약신청 : ‘26. 09. 18.(금) 10:00 ~ 09. 21. (월) 16:00 ■서류심사 대상자 발표 : ‘26. 09. 22. (화) 12:00 "
                + "■서류사본제출(이메일) : 개별 안내 ■계약체결 및 원본서류제출 : 개별 안내 [청약신청] ■ 문의전화 : 02-6459-9200");

        assertThat(events).containsExactlyInAnyOrderEntriesOf(Map.of(
                EventType.NOTICE, LocalDate.of(2026, 9, 17),
                EventType.APPLY_START, LocalDate.of(2026, 9, 18),
                EventType.APPLY_END, LocalDate.of(2026, 9, 21),
                EventType.RESULT, LocalDate.of(2026, 9, 22)));
    }

    @Test
    void hanyoung_resultAndContract() {
        Map<EventType, LocalDate> events = parse("[공급일정] ■모집공고 : ‘25. 07. 02. (수) "
                + "■청약신청 : ‘25. 07. 08. (화) 09:00 ~ 07. 10. (목) 18:00 ■당첨자 발표 : ‘25. 07. 14. (월) 15시 "
                + "■서류원본제출(등기우편) : ‘25. 07. 15. (화) ~ 07. 17.(목) ■계약체결 : ‘25. 07. 21. (월) 09:30 ~ 07. 23. (수) 17:00 ");

        assertThat(events).containsEntry(EventType.RESULT, LocalDate.of(2025, 7, 14))
                .containsEntry(EventType.DOCS_DUE, LocalDate.of(2025, 7, 17))
                .containsEntry(EventType.CONTRACT, LocalDate.of(2025, 7, 21))
                .doesNotContainKey(EventType.MOVE_IN);
    }

    @Test
    void rangeCrossingYear_endInNextYear() {
        Map<EventType, LocalDate> events = parse("[공급일정] ■청약신청 : ‘26. 12. 30. (수) 09:00 ~ 01. 04. (월) 18:00");

        assertThat(events).containsEntry(EventType.APPLY_START, LocalDate.of(2026, 12, 30))
                .containsEntry(EventType.APPLY_END, LocalDate.of(2027, 1, 4));
    }

    @Test
    void freeFormBody_noEvents() {
        assertThat(parser.parse("[청약신청] ■공급일정 - 신청기간: 2024년 2월 19일(월) 9시 ~ 20일(화) 18시")).isEmpty();
    }
}
