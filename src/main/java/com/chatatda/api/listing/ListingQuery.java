package com.chatatda.api.listing;

import com.chatatda.api.pricing.Grade;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

public record ListingQuery(Bbox bbox, Long maxRent, Long maxDeposit, Long maxMgmtFee, List<Grade> grades,
                           Integer maxWalkMin, RoomType roomType, Long buildingId, int page, int size) {

    public record Bbox(double minLng, double minLat, double maxLng, double maxLat) {
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
