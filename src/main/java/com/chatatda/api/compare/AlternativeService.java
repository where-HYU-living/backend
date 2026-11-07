package com.chatatda.api.compare;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.ErrorCode;
import com.chatatda.api.dorm.DormService;
import com.chatatda.api.dorm.DormService.DormDto;
import com.chatatda.api.dorm.DormService.RoomTypeDto;
import com.chatatda.api.geo.WalkService;
import com.chatatda.api.housing.HousingService;
import com.chatatda.api.housing.HousingService.UnitOption;
import com.chatatda.api.listing.Listing;
import com.chatatda.api.listing.ListingRepository;
import com.chatatda.api.pricing.PriceCalculator;
import com.chatatda.api.pricing.PricingProperties;
import com.chatatda.api.user.ProfileService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlternativeService {

    static final int LIMIT = 3;
    static final int COMPARE_MONTHS = 24;

    public record Alternative(String type, long id, String name, long convertedRent, long monthlyDiff, long twoYearDiff,
                              Integer walkMin) {
    }

    public record AlternativesDto(List<Alternative> housing, List<Alternative> dorms) {
    }

    private record Option(long id, String name, long convertedRent, double lat, double lng) {
    }

    private final ListingRepository listings;
    private final HousingService housing;
    private final DormService dorms;
    private final WalkService walk;
    private final ProfileService profiles;
    private final PriceCalculator calculator;
    private final PricingProperties properties;

    public AlternativeService(ListingRepository listings, HousingService housing, DormService dorms, WalkService walk,
                              ProfileService profiles, PricingProperties properties) {
        this.listings = listings;
        this.housing = housing;
        this.dorms = dorms;
        this.walk = walk;
        this.profiles = profiles;
        this.calculator = new PriceCalculator(properties);
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public AlternativesDto forListing(long listingId, Long buildingId, Long userIdOrNull) {
        Listing l = listings.findById(listingId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "매물을 찾을 수 없어요."));
        long base = calculator.convertedRent(l.getDeposit(), l.getRent(), l.getMgmtFee(), properties.conversionRate());
        Long building = buildingId != null ? buildingId
                : userIdOrNull == null ? null : profiles.firstBuildingId(userIdOrNull).orElse(null);

        List<Option> housingOptions = new ArrayList<>();
        for (UnitOption u : housing.unitOptions()) {
            housingOptions.add(new Option(u.housingId(), u.housingName() + " " + u.unitName(), u.convertedRent(), u.lat(), u.lng()));
        }
        List<Option> dormOptions = new ArrayList<>();
        for (DormDto d : dorms.list(null)) {
            for (RoomTypeDto r : d.roomTypes()) {
                dormOptions.add(new Option(d.id(), d.name() + " " + r.name(), r.monthlyCost(), d.lat(), d.lng()));
            }
        }
        return new AlternativesDto(cheapest("HOUSING", housingOptions, base, building),
                cheapest("DORM", dormOptions, base, building));
    }

    private List<Alternative> cheapest(String type, List<Option> options, long base, Long building) {
        return options.stream()
                .filter(o -> o.convertedRent() < base)
                .sorted(Comparator.comparingLong(Option::convertedRent))
                .limit(LIMIT)
                .map(o -> {
                    long diff = base - o.convertedRent();
                    return new Alternative(type, o.id(), o.name(), o.convertedRent(), diff, diff * COMPARE_MONTHS,
                            walk.minutesOrNull(o.lat(), o.lng(), building));
                })
                .toList();
    }
}
