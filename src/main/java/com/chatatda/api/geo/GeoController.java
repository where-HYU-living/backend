package com.chatatda.api.geo;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.ErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class GeoController {

    private final WalkService walkService;
    private final AddressPort addressPort;

    public GeoController(WalkService walkService, AddressPort addressPort) {
        this.walkService = walkService;
        this.addressPort = addressPort;
    }

    @GetMapping("/walk")
    public WalkService.WalkDto walk(@RequestParam double fromLat, @RequestParam double fromLng,
                                    @RequestParam long buildingId) {
        return walkService.route(fromLat, fromLng, buildingId);
    }

    @GetMapping("/geo/address")
    public List<AddressResult> address(@RequestParam String q) {
        if (q.isBlank()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "요청 값이 올바르지 않아요.", Map.of("q", "검색어를 입력해 주세요."));
        }
        try {
            return addressPort.search(q.trim());
        } catch (RuntimeException e) {
            throw new ApiException(ErrorCode.UPSTREAM_FAILED, "주소를 검색하지 못했어요. 잠시 뒤 다시 시도해 주세요.");
        }
    }
}
