package com.chatatda.api.realdeal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class RealDealNormalizerTest {

    private static final Instant NOW = Instant.parse("2026-11-07T00:00:00Z");

    private final RealDealNormalizer normalizer = new RealDealNormalizer();

    static String readXml(String name) {
        try (InputStream in = RealDealNormalizerTest.class.getResourceAsStream("/molit/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void officetel_convertsManwonAndFields() {
        var page = normalizer.parse(readXml("officetel-rent-sample.xml"), RealDealType.OFFICETEL, NOW);
        var first = page.deals().get(0);

        assertThat(first.getLawdCd()).isEqualTo("11200");
        assertThat(first.getBuildingType()).isEqualTo(RealDealType.OFFICETEL);
        assertThat(first.getDong()).isEqualTo("도선동");
        assertThat(first.getDeposit()).isEqualTo(10_000_000L);
        assertThat(first.getRent()).isEqualTo(1_100_000L);
        assertThat(first.getAreaM2()).isEqualTo(23.61);
        assertThat(first.getContractYm()).isEqualTo(YearMonth.of(2026, 10));
        assertThat(first.getFloor()).isEqualTo(8);
        assertThat(first.getBuiltYear()).isEqualTo(2017);
        assertThat(first.getContractType()).isEqualTo("신규");
        assertThat(first.getIngestedAt()).isEqualTo(NOW);
    }

    @Test
    void parsesAllItemsAndTotalCount() {
        var page = normalizer.parse(readXml("officetel-rent-sample.xml"), RealDealType.OFFICETEL, NOW);
        assertThat(page.totalCount()).isEqualTo(117);
        assertThat(page.itemCount()).isEqualTo(10);
        assertThat(page.deals()).hasSize(10);
        assertThat(page.skipped()).isZero();
    }

    @Test
    void rowhouse_usesExclusiveArea() {
        var page = normalizer.parse(readXml("rowhouse-rent-sample.xml"), RealDealType.ROWHOUSE, NOW);
        var first = page.deals().get(0);
        assertThat(first.getAreaM2()).isEqualTo(20.57);
        assertThat(first.getRent()).isZero();
        assertThat(first.getDeposit()).isEqualTo(210_000_000L);
    }

    @Test
    void detached_usesTotalFloorArea_andAllowsMissingFloorAndContractType() {
        var page = normalizer.parse(readXml("detached-rent-sample.xml"), RealDealType.DETACHED, NOW);
        var first = page.deals().get(0);

        assertThat(first.getDong()).isEqualTo("용답동");
        assertThat(first.getAreaM2()).isEqualTo(21.0);
        assertThat(first.getDeposit()).isEqualTo(30_000_000L);
        assertThat(first.getRent()).isEqualTo(600_000L);
        assertThat(first.getFloor()).isNull();
        assertThat(first.getContractType()).isNull();
        assertThat(first.getBuiltYear()).isEqualTo(1997);
    }

    @Test
    void skipsInvalidRows_keepsRenewalAndJeonseForLaterFiltering() {
        var page = normalizer.parse(readXml("invalid-rows.xml"), RealDealType.OFFICETEL, NOW);

        assertThat(page.itemCount()).isEqualTo(5);
        assertThat(page.skipped()).isEqualTo(4);
        assertThat(page.deals()).singleElement().satisfies(d -> {
            assertThat(d.getDong()).isEqualTo("행당동");
            assertThat(d.getContractType()).isEqualTo("갱신");
            assertThat(d.getRent()).isZero();
            assertThat(d.getFloor()).isEqualTo(-1);
            assertThat(d.getBuiltYear()).isEqualTo(2015);
        });
    }

    @Test
    void emptyItems_isEmptyPage() {
        var page = normalizer.parse(readXml("empty.xml"), RealDealType.OFFICETEL, NOW);
        assertThat(page.totalCount()).isZero();
        assertThat(page.deals()).isEmpty();
    }

    @Test
    void errorResponses_throw() {
        assertThatThrownBy(() -> normalizer.parse(readXml("error-service-key.xml"), RealDealType.OFFICETEL, NOW))
                .isInstanceOf(MolitApiException.class)
                .hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR");
        assertThatThrownBy(() -> normalizer.parse(readXml("error-result-code.xml"), RealDealType.OFFICETEL, NOW))
                .isInstanceOf(MolitApiException.class)
                .hasMessageContaining("22");
        assertThatThrownBy(() -> normalizer.parse("not xml at all", RealDealType.OFFICETEL, NOW))
                .isInstanceOf(MolitApiException.class);
    }

    @Test
    void rejectsDoctypeToPreventXxe() {
        String xxe = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><response>&x;</response>";
        assertThatThrownBy(() -> normalizer.parse(xxe, RealDealType.OFFICETEL, NOW))
                .isInstanceOf(MolitApiException.class);
    }
}
