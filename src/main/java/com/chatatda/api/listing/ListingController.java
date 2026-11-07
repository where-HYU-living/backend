package com.chatatda.api.listing;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.CurrentUser;
import com.chatatda.api.common.ErrorCode;
import com.chatatda.api.common.PageResponse;
import com.chatatda.api.geo.Place;
import com.chatatda.api.listing.ListingDtos.ListingDetail;
import com.chatatda.api.listing.ListingDtos.ListingSummary;
import com.chatatda.api.pricing.Grade;
import com.chatatda.api.pricing.PriceCheckService.PriceCheckDto;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/listings")
public class ListingController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_NEARBY_RADIUS = 500;

    private final ListingService service;

    public ListingController(ListingService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<ListingSummary> list(@RequestParam(required = false) String bbox,
                                             @RequestParam(required = false) Long maxRent,
                                             @RequestParam(required = false) Long maxDeposit,
                                             @RequestParam(required = false) Long maxMgmtFee,
                                             @RequestParam(required = false) List<Grade> grade,
                                             @RequestParam(required = false) Integer maxWalkMin,
                                             @RequestParam(required = false) RoomType roomType,
                                             @RequestParam(required = false) Long buildingId,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "20") int size,
                                             @CurrentUser(required = false) Long userId) {
        if (page < 0) {
            throw invalid("page", "0 이상이어야 해요.");
        }
        if (size < 1) {
            throw invalid("size", "1 이상이어야 해요.");
        }
        ListingQuery query = new ListingQuery(parseBbox(bbox), maxRent, maxDeposit, maxMgmtFee, grade, maxWalkMin,
                roomType, buildingId, page, Math.min(size, MAX_PAGE_SIZE));
        return service.list(query, userId);
    }

    @GetMapping("/{id}")
    public ListingDetail detail(@PathVariable long id, @RequestParam(required = false) Long buildingId,
                                @CurrentUser(required = false) Long userId) {
        return service.detail(id, buildingId, userId);
    }

    @GetMapping("/{id}/nearby")
    public List<Place> nearby(@PathVariable long id, @RequestParam(defaultValue = "300") int radius) {
        if (radius < 1) {
            throw invalid("radius", "1 이상이어야 해요.");
        }
        return service.nearby(id, Math.min(radius, MAX_NEARBY_RADIUS));
    }

    @GetMapping("/{id}/price-check")
    public PriceCheckDto priceCheck(@PathVariable long id, @CurrentUser(required = false) Long userId) {
        return service.priceCheck(id, userId);
    }

    private static ListingQuery.Bbox parseBbox(String bbox) {
        if (bbox == null || bbox.isBlank()) {
            return null;
        }
        String[] parts = bbox.split(",");
        if (parts.length != 4) {
            throw invalid("bbox", "minLng,minLat,maxLng,maxLat 형식이어야 해요.");
        }
        try {
            ListingQuery.Bbox b = new ListingQuery.Bbox(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()),
                    Double.parseDouble(parts[2].trim()), Double.parseDouble(parts[3].trim()));
            if (b.minLng() > b.maxLng() || b.minLat() > b.maxLat()) {
                throw invalid("bbox", "최솟값이 최댓값보다 클 수 없어요.");
            }
            return b;
        } catch (NumberFormatException e) {
            throw invalid("bbox", "숫자 4개여야 해요.");
        }
    }

    private static ApiException invalid(String field, String reason) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "요청 값이 올바르지 않아요.", Map.of(field, reason));
    }
}
