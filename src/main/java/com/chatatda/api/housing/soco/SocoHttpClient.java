package com.chatatda.api.housing.soco;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class SocoHttpClient implements SocoClient {

    private static final String HOME_LIST = "/youth/pgm/home/yohome/yoHomeListJson.json";
    private static final String HOME_VIEW = "/youth/pgm/home/yohome/view.do?menuNo=400002&homeCode=";
    private static final String NOTICE_LIST = "/youth/pgm/home/yohome/bbsListJson.json";
    private static final String NOTICE_BOARD = "BMSR00015";
    private static final int HOME_PAGE_SIZE = 500;

    private final String baseUrl;
    private final RestClient restClient;

    public SocoHttpClient(@Value("${external.soco.base-url}") String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(20_000);
        this.restClient = RestClient.builder().requestFactory(factory)
                .defaultHeader("User-Agent", "Mozilla/5.0 (compatible; chatatda-housing/1.0)")
                .build();
    }

    @Override
    public String homeList() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("pageNum", "1");
        form.add("rowCount", String.valueOf(HOME_PAGE_SIZE));
        return post(HOME_LIST, form);
    }

    @Override
    public String homeDetail(String homeCode) {
        return text(get(HOME_VIEW + URLEncoder.encode(homeCode, StandardCharsets.UTF_8)));
    }

    @Override
    public String notices(int pageIndex) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("bbsId", NOTICE_BOARD);
        form.add("pageIndex", String.valueOf(pageIndex));
        return post(NOTICE_LIST, form);
    }

    @Override
    public byte[] image(String path) {
        return get(path);
    }

    private String post(String path, MultiValueMap<String, String> form) {
        try {
            return text(restClient.post().uri(URI.create(baseUrl + path))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form).retrieve().body(byte[].class));
        } catch (RestClientException e) {
            throw new SocoApiException("soco 호출 실패: " + path + " " + e.getClass().getSimpleName(), e);
        }
    }

    private byte[] get(String path) {
        String url = path.startsWith("http") ? path : baseUrl + path;
        try {
            byte[] body = restClient.get().uri(URI.create(url)).retrieve().body(byte[].class);
            return body == null ? new byte[0] : body;
        } catch (RestClientException | IllegalArgumentException e) {
            throw new SocoApiException("soco 호출 실패: " + path + " " + e.getClass().getSimpleName(), e);
        }
    }

    private static String text(byte[] body) {
        return body == null ? "" : new String(body, StandardCharsets.UTF_8);
    }
}
