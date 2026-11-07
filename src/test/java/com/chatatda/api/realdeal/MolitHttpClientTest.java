package com.chatatda.api.realdeal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;
import org.junit.jupiter.api.Test;

class MolitHttpClientTest {

    @Test
    void buildsEndpointAndEncodesKeyExactlyOnce() {
        var client = new MolitHttpClient("https://apis.data.go.kr/1613000", "ab+c/d==");
        String uri = client.uri("11200", YearMonth.of(2026, 10), RealDealType.ROWHOUSE, 2, 1000).toString();

        assertThat(uri).startsWith("https://apis.data.go.kr/1613000/RTMSDataSvcRHRent/getRTMSDataSvcRHRent?");
        assertThat(uri).contains("serviceKey=ab%2Bc%2Fd%3D%3D");
        assertThat(uri).doesNotContain("%252B");
        assertThat(uri).contains("LAWD_CD=11200", "DEAL_YMD=202610", "pageNo=2", "numOfRows=1000");
    }

    @Test
    void eachTypeHasItsOwnEndpoint() {
        var client = new MolitHttpClient("https://apis.data.go.kr/1613000", "k");
        assertThat(client.uri("11200", YearMonth.of(2026, 10), RealDealType.OFFICETEL, 1, 10).toString())
                .contains("/RTMSDataSvcOffiRent/getRTMSDataSvcOffiRent?");
        assertThat(client.uri("11200", YearMonth.of(2026, 10), RealDealType.DETACHED, 1, 10).toString())
                .contains("/RTMSDataSvcSHRent/getRTMSDataSvcSHRent?");
    }

    @Test
    void missingKey_failsOnlyWhenCalled() {
        var client = new MolitHttpClient("https://apis.data.go.kr/1613000", "");
        assertThatThrownBy(() -> client.fetch("11200", YearMonth.of(2026, 10), RealDealType.OFFICETEL, 1, 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("external.molit.key");
    }
}
