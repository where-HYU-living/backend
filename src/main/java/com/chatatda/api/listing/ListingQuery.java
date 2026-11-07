package com.chatatda.api.listing;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.ErrorCode;
import com.chatatda.api.pricing.Grade;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;

public record ListingQuery(Bbox bbox, Long maxRent, Long maxDeposit, Long maxMgmtFee, List<Grade> grades,
                           Integer maxWalkMin, RoomType roomType, Long buildingId, int page, int size) {

    public record Bbox(double minLng, double minLat, double maxLng, double maxLat) {

        public boolean contains(double lat, double lng) {
            return lng >= minLng && lng <= maxLng && lat >= minLat && lat <= maxLat;
        }

        public static Bbox parse(String bbox) {
            if (bbox == null || bbox.isBlank()) {
                return null;
            }
            String[] parts = bbox.split(",");
            if (parts.length != 4) {
                throw invalid("minLng,minLat,maxLng,maxLat 형식이어야 해요.");
            }
            try {
                Bbox b = new Bbox(Double.parseDouble(parts[0].trim()), Double.parseDouble(parts[1].trim()),
                        Double.parseDouble(parts[2].trim()), Double.parseDouble(parts[3].trim()));
                if (b.minLng() > b.maxLng() || b.minLat() > b.maxLat()) {
                    throw invalid("최솟값이 최댓값보다 클 수 없어요.");
                }
                return b;
            } catch (NumberFormatException e) {
                throw invalid("숫자 4개여야 해요.");
            }
        }

        private static ApiException invalid(String reason) {
            return new ApiException(ErrorCode.VALIDATION_FAILED, "요청 값이 올바르지 않아요.", Map.of("bbox", reason));
        }
    }

    public Specification<Listing> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            if (bbox != null) {
                where.add(cb.between(root.get("lng"), bbox.minLng(), bbox.maxLng()));
                where.add(cb.between(root.get("lat"), bbox.minLat(), bbox.maxLat()));
            }
            if (maxRent != null) {
                where.add(cb.le(root.get("rent"), maxRent));
            }
            if (maxDeposit != null) {
                where.add(cb.le(root.get("deposit"), maxDeposit));
            }
            if (maxMgmtFee != null) {
                where.add(cb.le(root.get("mgmtFee"), maxMgmtFee));
            }
            if (grades != null && !grades.isEmpty()) {
                where.add(root.get("grade").in(grades));
            }
            if (roomType != null) {
                where.add(cb.equal(root.get("roomType"), roomType));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }
}
