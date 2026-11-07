package com.chatatda.api.favorite;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.ErrorCode;
import com.chatatda.api.dorm.DormService;
import com.chatatda.api.dorm.DormService.DormDto;
import com.chatatda.api.dorm.DormService.RoomTypeDto;
import com.chatatda.api.housing.HousingDtos.HousingDetail;
import com.chatatda.api.housing.HousingService;
import com.chatatda.api.listing.ListingDtos.ListingSummary;
import com.chatatda.api.listing.ListingRepository;
import com.chatatda.api.listing.ListingService;
import com.chatatda.api.pricing.Grade;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FavoriteService {

    public record FavoriteItem(FavoriteType type, long targetId, String name, double lat, double lng,
                               Long convertedRent, Grade grade, Integer walkMin, Instant createdAt) {
    }

    private record Target(String name, double lat, double lng, Long convertedRent, Grade grade, Integer walkMin) {
    }

    private final FavoriteRepository favorites;
    private final ListingRepository listings;
    private final ListingService listingService;
    private final HousingService housing;
    private final DormService dorms;
    private final Clock clock;

    public FavoriteService(FavoriteRepository favorites, ListingRepository listings, ListingService listingService,
                           HousingService housing, DormService dorms, Clock clock) {
        this.favorites = favorites;
        this.listings = listings;
        this.listingService = listingService;
        this.housing = housing;
        this.dorms = dorms;
        this.clock = clock;
    }

    public List<FavoriteItem> list(long userId, FavoriteType typeOrNull) {
        List<Favorite> rows = typeOrNull == null
                ? favorites.findByUserIdOrderByCreatedAtDescIdDesc(userId)
                : favorites.findByUserIdAndTypeOrderByCreatedAtDescIdDesc(userId, typeOrNull);
        List<FavoriteItem> items = new ArrayList<>();
        for (Favorite f : rows) {
            try {
                items.add(item(f, target(f.getType(), f.getTargetId(), userId)));
            } catch (ApiException e) {
                if (e.code() != ErrorCode.NOT_FOUND) {
                    throw e;
                }
            }
        }
        return items;
    }

    @Transactional
    public FavoriteItem add(long userId, FavoriteType type, long targetId) {
        Target target = target(type, targetId, userId);
        if (favorites.existsByUserIdAndTypeAndTargetId(userId, type, targetId)) {
            throw alreadyExists();
        }
        try {
            Favorite saved = favorites.saveAndFlush(new Favorite(userId, type, targetId, clock.instant()));
            return item(saved, target);
        } catch (DataIntegrityViolationException e) {
            throw alreadyExists();
        }
    }

    @Transactional
    public void remove(long userId, FavoriteType type, long targetId) {
        favorites.deleteOne(userId, type, targetId);
    }

    private Target target(FavoriteType type, long targetId, Long userId) {
        return switch (type) {
            case LISTING -> {
                ListingSummary s = listingService.summaryOf(listings.findById(targetId)
                        .orElseThrow(() -> notFound("매물")), null, userId);
                yield new Target(s.title(), s.lat(), s.lng(), s.convertedRent(), s.grade(), s.walkMin());
            }
            case HOUSING -> {
                HousingDetail h = housing.detail(targetId, null, userId);
                yield new Target(h.name(), h.lat(), h.lng(), h.minConvertedRent(), null, h.walkMin());
            }
            case DORM -> {
                DormDto d = dorms.detail(targetId, null, userId);
                Long cheapest = d.roomTypes().stream().map(RoomTypeDto::monthlyCost).min(Long::compare).orElse(null);
                yield new Target(d.name(), d.lat(), d.lng(), cheapest, null, d.walkMin());
            }
            case TRANSFER -> throw notFound("양도 글");
        };
    }

    private static FavoriteItem item(Favorite f, Target t) {
        return new FavoriteItem(f.getType(), f.getTargetId(), t.name(), t.lat(), t.lng(), t.convertedRent(), t.grade(),
                t.walkMin(), f.getCreatedAt());
    }

    private static ApiException notFound(String what) {
        return new ApiException(ErrorCode.NOT_FOUND, what + "을(를) 찾을 수 없어요.");
    }

    private static ApiException alreadyExists() {
        return new ApiException(ErrorCode.ALREADY_EXISTS, "이미 찜했어요.");
    }
}
