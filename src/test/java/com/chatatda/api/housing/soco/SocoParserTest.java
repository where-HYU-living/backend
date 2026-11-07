package com.chatatda.api.housing.soco;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class SocoParserTest {

    private final SocoParser parser = new SocoParser(new ObjectMapper());

    private static String resource(String name) throws IOException {
        try (InputStream in = SocoParserTest.class.getResourceAsStream("/soco/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void homeList_parsesCodesAndAddresses() throws IOException {
        List<SocoHome> homes = parser.homes(resource("home-list.json"));

        assertThat(homes).hasSize(6);
        assertThat(homes).extracting(SocoHome::homeCode, SocoHome::name, SocoHome::address)
                .contains(tuple("20000580", "왕십리역 라봄성동", "서울 성동구 마장동 339-2 왕십리역 라봄성동"));
    }

    @Test
    void view_generalSupplyYouthOnly() throws IOException {
        SocoHomeDetail detail = parser.detail(resource("view-labom.html"));

        assertThat(detail.units()).extracting(SocoUnit::name, SocoUnit::deposit, SocoUnit::rent, SocoUnit::mgmtFee)
                .containsExactly(
                        tuple("19A", 114_000_000L, 490_000L, 90_550L),
                        tuple("19B", 114_000_000L, 490_000L, 89_930L),
                        tuple("20", 119_000_000L, 510_000L, 93_490L),
                        tuple("25A", 143_000_000L, 490_000L, 115_980L),
                        tuple("25B", 142_000_000L, 500_000L, 114_750L));
        assertThat(detail.units()).allSatisfy(u -> assertThat(u.roomKind()).isEqualTo("원룸"));
    }

    @Test
    void view_areaFromParentheses() throws IOException {
        SocoHomeDetail detail = parser.detail(resource("view-cheongajin.html"));

        assertThat(detail.units()).extracting(SocoUnit::name, SocoUnit::exclusiveAreaM2, SocoUnit::rent)
                .containsExactly(
                        tuple("18타입(18.22)", 18.22, 620_000L),
                        tuple("18A타입(18.22)", 18.22, 620_000L));
    }

    @Test
    void view_areaMissing_null() throws IOException {
        SocoHomeDetail detail = parser.detail(resource("view-labom.html"));

        assertThat(detail.units()).allSatisfy(u -> assertThat(u.exclusiveAreaM2()).isNull());
    }

    @Test
    void view_coordinatesPhoneImages() throws IOException {
        SocoHomeDetail detail = parser.detail(resource("view-cheongajin.html"));

        assertThat(detail.lat()).isEqualTo(37.5651154);
        assertThat(detail.lng()).isEqualTo(127.0348084);
        assertThat(detail.phone()).isEqualTo("02-6273-0266");
        assertThat(detail.homepage()).isEqualTo("https://caj266.com");
        assertThat(detail.floorplanImageUrls()).containsExactly(
                "/cohome/cmmn/file/fileDown.do?atchFileId=c6233e091c1c4c1093f8bf9632112398&fileSn=3",
                "/cohome/cmmn/file/fileDown.do?atchFileId=c6233e091c1c4c1093f8bf9632112398&fileSn=4");
    }

    @Test
    void notices_titleDateAndBodyText() throws IOException {
        List<SocoNotice> notices = parser.notices(resource("notices.json"));

        assertThat(notices).hasSize(5);
        SocoNotice cheongajin = notices.stream().filter(n -> n.boardId() == 6586).findFirst().orElseThrow();
        assertThat(cheongajin.title()).isEqualTo("[민간임대] 왕십리역 청아진 최초모집공고");
        assertThat(cheongajin.postedOn()).isEqualTo(LocalDate.of(2026, 7, 7));
        assertThat(cheongajin.bodyText()).contains("[공급일정]").contains("■청약신청").doesNotContain("<p");
    }
}
