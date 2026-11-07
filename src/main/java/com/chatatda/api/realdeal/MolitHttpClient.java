package com.chatatda.api.realdeal;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class MolitHttpClient implements MolitClient {

    private static final Map<RealDealType, String> ENDPOINTS = Map.of(
            RealDealType.OFFICETEL, "RTMSDataSvcOffiRent/getRTMSDataSvcOffiRent",
            RealDealType.ROWHOUSE, "RTMSDataSvcRHRent/getRTMSDataSvcRHRent",
            RealDealType.DETACHED, "RTMSDataSvcSHRent/getRTMSDataSvcSHRent");
    private static final DateTimeFormatter DEAL_YMD = DateTimeFormatter.ofPattern("yyyyMM");

    private final String baseUrl;
    private final String serviceKey;
    private final RestClient restClient;

    public MolitHttpClient(@Value("${external.molit.base-url}") String baseUrl,
                           @Value("${external.molit.key:}") String serviceKey) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.serviceKey = serviceKey == null ? "" : serviceKey.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(15_000);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public String fetch(String lawdCd, YearMonth ym, RealDealType type, int pageNo, int numOfRows) {
        if (serviceKey.isEmpty()) {
            throw new IllegalStateException("external.molit.key (환경 변수 MOLIT_KEY) 가 비어 있어요.");
        }
        try {
            byte[] body = restClient.get().uri(uri(lawdCd, ym, type, pageNo, numOfRows)).retrieve().body(byte[].class);
            return body == null ? "" : new String(body, StandardCharsets.UTF_8);
        } catch (RestClientException e) {
            throw new MolitApiException("국토부 API 호출 실패: " + e.getClass().getSimpleName(), e);
        }
    }

    URI uri(String lawdCd, YearMonth ym, RealDealType type, int pageNo, int numOfRows) {
        return URI.create(baseUrl + "/" + ENDPOINTS.get(type)
                + "?serviceKey=" + URLEncoder.encode(serviceKey, StandardCharsets.UTF_8)
                + "&LAWD_CD=" + lawdCd
                + "&DEAL_YMD=" + ym.format(DEAL_YMD)
                + "&pageNo=" + pageNo
                + "&numOfRows=" + numOfRows);
    }
}
