package com.chatatda.api.transfer;

import com.chatatda.api.listing.RoomType;
import com.chatatda.api.pricing.Grade;
import com.chatatda.api.pricing.PriceCheckService.PriceCheckDto;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public final class TransferDtos {

    private TransferDtos() {
    }

    public record TransferRequest(
            @NotBlank @Size(min = 2, max = 40) String title,
            @NotBlank @Size(max = 300) String address,
            @NotBlank @Size(max = 50) String dong,
            @NotNull @DecimalMin("33") @DecimalMax("39") Double lat, // 국내 좌표만 (잘못된 좌표가 도보 계산을 망치지 않게)
            @NotNull @DecimalMin("124") @DecimalMax("132") Double lng,
            @NotNull @PositiveOrZero Long deposit,
            @NotNull @PositiveOrZero Long rent,
            @PositiveOrZero Long mgmtFee,
            @NotNull @Positive Double areaM2,
            @NotNull RoomType roomType,
            Integer floor,
            @NotNull @Min(1) @Max(60) Integer remainingMonths,
            LocalDate availableFrom,
            @Size(max = 2000) String description,
            @Size(max = 10) List<String> photoUrls) {
    }

    public record StatusRequest(@NotNull TransferStatus status) {
    }

    public record TransferSummary(long id, String title, String address, String dong, double lat, double lng,
                                  long deposit, long rent, Long mgmtFee, double areaM2, Integer floor,
                                  RoomType roomType, long convertedRent, Grade grade, Integer walkMin,
                                  String thumbnailUrl, boolean favorited, int remainingMonths,
                                  LocalDate availableFrom, TransferStatus status, String authorNickname) {
        public TransferSummary withWalkMin(Integer minutes) {
            return new TransferSummary(id, title, address, dong, lat, lng, deposit, rent, mgmtFee, areaM2, floor, roomType,
                    convertedRent, grade, minutes, thumbnailUrl, favorited, remainingMonths, availableFrom, status, authorNickname);
        }
    }

    public record TransferDetail(long id, String title, String address, String dong, double lat, double lng,
                                 long deposit, long rent, Long mgmtFee, double areaM2, Integer floor,
                                 RoomType roomType, long convertedRent, Grade grade, Integer walkMin,
                                 String thumbnailUrl, boolean favorited, int remainingMonths,
                                 LocalDate availableFrom, TransferStatus status, String authorNickname,
                                 String description, List<String> photos, PriceCheckDto priceCheck, boolean mine) {

        static TransferDetail of(TransferSummary s, Transfer t, PriceCheckDto priceCheck, boolean mine) {
            return new TransferDetail(s.id(), s.title(), s.address(), s.dong(), s.lat(), s.lng(), s.deposit(), s.rent(),
                    s.mgmtFee(), s.areaM2(), s.floor(), s.roomType(), s.convertedRent(), s.grade(), s.walkMin(),
                    s.thumbnailUrl(), s.favorited(), s.remainingMonths(), s.availableFrom(), s.status(),
                    s.authorNickname(), t.getDescription(), List.copyOf(t.getPhotos()), priceCheck, mine);
        }
    }
}
