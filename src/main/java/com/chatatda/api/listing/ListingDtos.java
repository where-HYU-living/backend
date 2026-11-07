package com.chatatda.api.listing;

import com.chatatda.api.pricing.Grade;
import java.util.List;

public final class ListingDtos {

    private ListingDtos() {
    }

    public record ListingSummary(long id, String title, String address, String dong, double lat, double lng,
                                 long deposit, long rent, Long mgmtFee, double areaM2, Integer floor,
                                 RoomType roomType, long convertedRent, Grade grade, Integer walkMin,
                                 String thumbnailUrl, boolean favorited) {
    }

    public record ListingDetail(long id, String title, String address, String dong, double lat, double lng,
                                long deposit, long rent, Long mgmtFee, double areaM2, Integer floor,
                                RoomType roomType, long convertedRent, Grade grade, Integer walkMin,
                                String thumbnailUrl, boolean favorited,
                                List<String> options, List<String> photos, Long floorplanId, Integer builtYear,
                                String description) {

        static ListingDetail of(ListingSummary s, Listing l) {
            return new ListingDetail(s.id(), s.title(), s.address(), s.dong(), s.lat(), s.lng(), s.deposit(), s.rent(),
                    s.mgmtFee(), s.areaM2(), s.floor(), s.roomType(), s.convertedRent(), s.grade(), s.walkMin(),
                    s.thumbnailUrl(), s.favorited(), List.copyOf(l.getOptions()), List.copyOf(l.getPhotos()),
                    l.getFloorplanId(), l.getBuiltYear(), l.getDescription());
        }
    }
}
