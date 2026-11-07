package com.chatatda.api.review;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

public final class ReviewDtos {

    private ReviewDtos() {
    }

    public record Scores(
            @NotNull @Min(1) @Max(5) Integer noise,
            @NotNull @Min(1) @Max(5) Integer light,
            @NotNull @Min(1) @Max(5) Integer bugs,
            @NotNull @Min(1) @Max(5) Integer landlord,
            @NotNull @Min(1) @Max(5) Integer depositReturn) {
    }

    public record ReviewRequest(
            @NotNull ReviewTargetType targetType,
            @NotNull Long targetId,
            @NotNull @Min(1) @Max(5) Integer rating,
            @NotNull @Valid Scores scores,
            @Size(max = 500) String text,
            @Size(min = 3, max = 3) List<@NotNull Double> pin,
            Long floorplanId) {
    }

    public record ReviewItem(long id, int rating, Scores scores, String text, double[] pin, Long floorplanId,
                             String authorNickname, Instant createdAt) {
    }

    public record ReviewList(Double average, int count, List<ReviewItem> items) {
    }
}
