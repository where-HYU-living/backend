package com.chatatda.api.listing;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.ErrorCode;
import com.chatatda.api.common.PageResponse;
import com.chatatda.api.geo.NearbyService;
import com.chatatda.api.geo.Place;
import com.chatatda.api.geo.WalkService;
import com.chatatda.api.listing.ListingDtos.ListingDetail;
import com.chatatda.api.listing.ListingDtos.ListingSummary;
import com.chatatda.api.pricing.Grade;
import com.chatatda.api.pricing.PriceCalculator;
import com.chatatda.api.pricing.PriceCheckService;
import com.chatatda.api.pricing.PriceCheckService.PriceCheckDto;
import com.chatatda.api.pricing.PriceInput;
import com.chatatda.api.pricing.PriceResult;
import com.chatatda.api.pricing.PricingProperties;
import com.chatatda.api.user.ProfileService;
import java.time.Clock;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ListingService {

    private static final Sort BY_ID = Sort.by("id");

    private final ListingRepository listings;
    private final PriceCheckService priceCheck;
    private final WalkService walk;
    private final NearbyService nearby;
    private final ProfileService profiles;
    private final PricingProperties properties;
    private final PriceCalculator calculator;
    private final Clock clock;

    public ListingService(ListingRepository listings, PriceCheckService priceCheck, WalkService walk,
                          NearbyService nearby, ProfileService profiles, PricingProperties properties, Clock clock) {
        this.listings = listings;
        this.priceCheck = priceCheck;
        this.walk = walk;
        this.nearby = nearby;
        this.profiles = profiles;
        this.properties = properties;
        this.calculator = new PriceCalculator(properties);
        this.clock = clock;
    }

    @Transactional
    public int recomputeAll() {
        List<Listing> all = listings.findAll();
        for (Listing l : all) {
            PriceResult r = priceCheck.evaluate(input(l));
            l.applyPrice(r.convertedRent(), r.grade(), clock.instant());
        }
        return all.size();
    }

    @Transactional(readOnly = true)
    public PageResponse<ListingSummary> list(ListingQuery q, Long userIdOrNull) {
        Long buildingId = effectiveBuilding(q.buildingId(), userIdOrNull);

        if (buildingId != null && q.maxWalkMin() != null) {
            List<ListingSummary> filtered = listings.findAll(q.toSpecification(), BY_ID).stream()
                    .map(l -> summary(l, buildingId))
                    .filter(s -> s.walkMin() != null && s.walkMin() <= q.maxWalkMin())
                    .toList();
            int from = Math.min(q.page() * q.size(), filtered.size());
            int to = Math.min(from + q.size(), filtered.size());
            return new PageResponse<>(filtered.subList(from, to), q.page(), q.size(), filtered.size());
        }

        Page<Listing> page = listings.findAll(q.toSpecification(), PageRequest.of(q.page(), q.size(), BY_ID));
        List<ListingSummary> items = page.getContent().stream().map(l -> summary(l, buildingId)).toList();
        return new PageResponse<>(items, q.page(), q.size(), page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public ListingDetail detail(long id, Long buildingId, Long userIdOrNull) {
        Listing l = load(id);
        return ListingDetail.of(summary(l, effectiveBuilding(buildingId, userIdOrNull)), l);
    }

    @Transactional(readOnly = true)
    public PriceCheckDto priceCheck(long id, Long userIdOrNull) {
        return priceCheck.check(input(load(id)), userIdOrNull);
    }

    @Transactional(readOnly = true)
    public List<Place> nearby(long id, int radius) {
        Listing l = load(id);
        return nearby.near(l.getLat(), l.getLng(), radius);
    }

    public ListingSummary summaryOf(Listing l, Long buildingId, Long userIdOrNull) {
        return summary(l, effectiveBuilding(buildingId, userIdOrNull));
    }

    private ListingSummary summary(Listing l, Long effectiveBuildingId) {
        long converted = l.getConvertedRent() != null ? l.getConvertedRent()
                : calculator.convertedRent(l.getDeposit(), l.getRent(), l.getMgmtFee(), properties.conversionRate());
        Grade grade = l.getGrade() != null ? l.getGrade() : Grade.PENDING;
        return new ListingSummary(l.getId(), l.getTitle(), l.getAddress(), l.getDong(), l.getLat(), l.getLng(),
                l.getDeposit(), l.getRent(), l.getMgmtFee(), l.getAreaM2(), l.getFloor(), l.getRoomType(), converted,
                grade, walk.minutesOrNull(l.getLat(), l.getLng(), effectiveBuildingId), l.getThumbnailUrl(), false);
    }

    private Long effectiveBuilding(Long buildingId, Long userIdOrNull) {
        if (buildingId != null) {
            return buildingId;
        }
        return userIdOrNull == null ? null : profiles.firstBuildingId(userIdOrNull).orElse(null);
    }

    private PriceInput input(Listing l) {
        return new PriceInput(l.getDeposit(), l.getRent(), l.getMgmtFee(), l.getAreaM2(), l.getDong(),
                properties.defaultContractMonths());
    }

    private Listing load(long id) {
        return listings.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "매물을 찾을 수 없어요."));
    }
}
