package com.chatatda.api.housing;

import java.time.LocalDate;
import java.util.List;

public final class HousingDtos {

    private HousingDtos() {
    }

    public record EventDto(EventType type, LocalDate date) {
    }

    public record HousingSummary(long id, String name, String address, double lat, double lng, boolean recruiting,
                                 EventDto nextEvent, boolean subscribed, boolean favorited, Long minConvertedRent,
                                 Integer walkMin) {
    }

    public record UnitTypeDto(long id, String name, Double exclusiveAreaM2, long deposit, long rent, Long mgmtFee,
                              long convertedRent, Long floorplanId) {
    }

    public record HousingDetail(long id, String name, String address, double lat, double lng, boolean recruiting,
                                EventDto nextEvent, boolean subscribed, boolean favorited, Long minConvertedRent,
                                Integer walkMin,
                                List<UnitTypeDto> unitTypes, String floorLayoutImageUrl, String contact) {
    }

    public record AnnouncementDto(long id, long housingId, String title, AnnouncementStatus status,
                                  List<EventDto> events) {
    }
}
