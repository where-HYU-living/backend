package com.chatatda.api.search;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.ErrorCode;
import com.chatatda.api.geo.AddressPort;
import com.chatatda.api.geo.AddressResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * A5 통합 검색 (계약 5-3): 매물 → 청년안심주택 → 기숙사 → 양도 글(양도 중) → 캠퍼스 건물 순으로 DB 결과를 먼저,
 * 모자라면 카카오 주소 검색(ADDRESS)으로 채운다. 주소 검색이 실패해도 DB 결과는 200 으로 준다.
 */
@RestController
@RequestMapping("/api/v1/search")
public class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);
    private static final int MAX_LIMIT = 30;

    public record SearchResult(String type, Long id, String name, String address, double lat, double lng) {
    }

    private final JdbcTemplate jdbc;
    private final AddressPort address;

    public SearchController(JdbcTemplate jdbc, AddressPort address) {
        this.jdbc = jdbc;
        this.address = address;
    }

    @GetMapping
    public List<SearchResult> search(@RequestParam String q, @RequestParam(defaultValue = "10") int limit) {
        String term = q == null ? "" : q.strip();
        if (term.length() < 2) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "요청 값이 올바르지 않아요.", Map.of("q", "2자 이상이어야 해요."));
        }
        if (limit < 1) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "요청 값이 올바르지 않아요.", Map.of("limit", "1 이상이어야 해요."));
        }
        int n = Math.min(limit, MAX_LIMIT);
        String like = "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        List<SearchResult> out = new ArrayList<>();
        add(out, n, "LISTING", "select id, title as name, address, lat, lng from listings where title ilike ? or address ilike ? or dong ilike ? order by id limit ?", like, like, like);
        add(out, n, "HOUSING", "select id, name, address, lat, lng from housing where name ilike ? or address ilike ? or coalesce(dong, '') ilike ? order by id limit ?", like, like, like);
        add(out, n, "DORM", "select id, name, null as address, lat, lng from dorms where name ilike ? order by id limit ?", like);
        add(out, n, "TRANSFER", "select id, title as name, address, lat, lng from transfers where status = 'OPEN' and (title ilike ? or address ilike ? or dong ilike ?) order by id desc limit ?", like, like, like);
        add(out, n, "BUILDING", "select id, name, null as address, lat, lng from buildings where type <> 'DORM' and name ilike ? order by id limit ?", like);
        if (out.size() < n) {
            try {
                for (AddressResult a : address.search(term)) {
                    if (out.size() >= n) {
                        break;
                    }
                    String label = a.roadAddress() != null ? a.roadAddress() : a.address();
                    out.add(new SearchResult("ADDRESS", null, label, a.address(), a.lat(), a.lng()));
                }
            } catch (RuntimeException e) {
                log.debug("주소 검색 실패 — DB 결과만 응답: {}", e.getMessage());
            }
        }
        return out;
    }

    private void add(List<SearchResult> out, int n, String type, String sql, Object... likes) {
        int left = n - out.size();
        if (left <= 0) {
            return;
        }
        Object[] args = new Object[likes.length + 1];
        System.arraycopy(likes, 0, args, 0, likes.length);
        args[likes.length] = left;
        out.addAll(jdbc.query(sql, (rs, i) -> new SearchResult(type, rs.getLong("id"), rs.getString("name"),
                rs.getString("address"), rs.getDouble("lat"), rs.getDouble("lng")), args));
    }
}
