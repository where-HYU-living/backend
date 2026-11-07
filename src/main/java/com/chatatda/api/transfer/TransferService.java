package com.chatatda.api.transfer;

import com.chatatda.api.common.ApiException;
import com.chatatda.api.common.ErrorCode;
import com.chatatda.api.common.PageResponse;
import com.chatatda.api.favorite.FavoriteRepository;
import com.chatatda.api.favorite.FavoriteType;
import com.chatatda.api.geo.WalkService;
import com.chatatda.api.listing.ListingQuery.Bbox;
import com.chatatda.api.pricing.Grade;
import com.chatatda.api.pricing.PriceCheckService;
import com.chatatda.api.pricing.PriceInput;
import com.chatatda.api.pricing.PriceResult;
import com.chatatda.api.transfer.TransferDtos.TransferDetail;
import com.chatatda.api.transfer.TransferDtos.TransferRequest;
import com.chatatda.api.transfer.TransferDtos.TransferSummary;
import com.chatatda.api.user.ProfileService;
import com.chatatda.api.user.User;
import com.chatatda.api.user.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferService {

    private static final Sort NEWEST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final TransferRepository transfers;
    private final UserRepository users;
    private final FavoriteRepository favorites;
    private final PriceCheckService priceCheck;
    private final WalkService walk;
    private final ProfileService profiles;
    private final StoragePort storage;
    private final Clock clock;

    public TransferService(TransferRepository transfers, UserRepository users, FavoriteRepository favorites,
                           PriceCheckService priceCheck, WalkService walk, ProfileService profiles, StoragePort storage,
                           Clock clock) {
        this.transfers = transfers;
        this.users = users;
        this.favorites = favorites;
        this.priceCheck = priceCheck;
        this.walk = walk;
        this.profiles = profiles;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<TransferSummary> list(Bbox bbox, TransferStatus status, int page, int size, Long userIdOrNull) {
        Page<Transfer> rows = transfers.findAll(spec(bbox, status), PageRequest.of(page, size, NEWEST));
        Map<Long, String> nicknames = nicknames(rows.getContent());
        Set<Long> favorited = userIdOrNull == null ? Set.of() : favorites.findTargetIds(userIdOrNull, FavoriteType.TRANSFER);
        Long building = effectiveBuilding(userIdOrNull);
        List<TransferSummary> items = rows.getContent().stream()
                .map(t -> summary(t, nicknames.get(t.getAuthorId()), favorited.contains(t.getId()), building))
                .toList();
        return new PageResponse<>(items, page, size, rows.getTotalElements());
    }

    @Transactional(readOnly = true)
    public TransferDetail detail(long id, Long userIdOrNull) {
        return detailOf(load(id), userIdOrNull);
    }

    @Transactional(readOnly = true)
    public TransferSummary summaryOf(long id, Long userIdOrNull) {
        Transfer t = load(id);
        boolean favorited = userIdOrNull != null
                && favorites.existsByUserIdAndTypeAndTargetId(userIdOrNull, FavoriteType.TRANSFER, id);
        return summary(t, nicknames(List.of(t)).get(t.getAuthorId()), favorited, effectiveBuilding(userIdOrNull));
    }

    @Transactional
    public TransferDetail create(long userId, TransferRequest request) {
        checkRequest(request);
        Transfer t = new Transfer(userId, clock.instant());
        t.apply(request, clock.instant());
        price(t);
        return detailOf(transfers.save(t), userId);
    }

    @Transactional
    public TransferDetail update(long userId, long id, TransferRequest request) {
        Transfer t = loadOwned(userId, id);
        checkRequest(request);
        t.apply(request, clock.instant());
        price(t);
        return detailOf(t, userId);
    }

    @Transactional
    public TransferDetail changeStatus(long userId, long id, TransferStatus status) {
        Transfer t = loadOwned(userId, id);
        t.changeStatus(status, clock.instant());
        return detailOf(t, userId);
    }

    @Transactional
    public int recomputeAll() {
        List<Transfer> all = transfers.findAll();
        all.forEach(this::price);
        return all.size();
    }

    private void price(Transfer t) {
        PriceResult r = priceCheck.evaluate(input(t));
        t.applyPrice(r.convertedRent(), r.grade());
    }

    private TransferDetail detailOf(Transfer t, Long userIdOrNull) {
        boolean favorited = userIdOrNull != null
                && favorites.existsByUserIdAndTypeAndTargetId(userIdOrNull, FavoriteType.TRANSFER, t.getId());
        TransferSummary s = summary(t, nicknames(List.of(t)).get(t.getAuthorId()), favorited, effectiveBuilding(userIdOrNull));
        boolean mine = userIdOrNull != null && userIdOrNull == t.getAuthorId();
        return TransferDetail.of(s, t, priceCheck.check(input(t), userIdOrNull), mine);
    }

    private TransferSummary summary(Transfer t, String nickname, boolean favorited, Long building) {
        long converted = t.getConvertedRent() != null ? t.getConvertedRent() : priceCheck.evaluate(input(t)).convertedRent();
        Grade grade = t.getGrade() != null ? t.getGrade() : Grade.PENDING;
        String thumbnail = t.getPhotos().isEmpty() ? null : t.getPhotos().get(0);
        return new TransferSummary(t.getId(), t.getTitle(), t.getAddress(), t.getDong(), t.getLat(), t.getLng(),
                t.getDeposit(), t.getRent(), t.getMgmtFee(), t.getAreaM2(), t.getFloor(), t.getRoomType(), converted,
                grade, walk.minutesOrNull(t.getLat(), t.getLng(), building), thumbnail, favorited,
                t.getRemainingMonths(), t.getAvailableFrom(), t.getStatus(), nickname);
    }

    private void checkRequest(TransferRequest request) {
        int titleLength = request.title().trim().length();
        if (titleLength < 2 || titleLength > 40) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "요청 값이 올바르지 않아요.",
                    Map.of("title", "앞뒤 공백을 빼고 2~40자여야 해요."));
        }
        if (request.photoUrls() == null) {
            return;
        }
        for (String url : request.photoUrls()) {
            if (!storage.owns(url)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "요청 값이 올바르지 않아요.",
                        Map.of("photoUrls", "/uploads 로 올린 사진 주소만 쓸 수 있어요."));
            }
        }
    }

    private static PriceInput input(Transfer t) {
        return new PriceInput(t.getDeposit(), t.getRent(), t.getMgmtFee(), t.getAreaM2(), t.getDong(),
                t.getRemainingMonths());
    }

    private Map<Long, String> nicknames(List<Transfer> rows) {
        Set<Long> ids = rows.stream().map(Transfer::getAuthorId).collect(Collectors.toSet());
        Map<Long, String> map = new HashMap<>();
        for (User u : users.findAllById(ids)) {
            map.put(u.getId(), u.getNickname());
        }
        return map;
    }

    private Long effectiveBuilding(Long userIdOrNull) {
        return userIdOrNull == null ? null : profiles.firstBuildingId(userIdOrNull).orElse(null);
    }

    private Transfer load(long id) {
        return transfers.findById(id).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "양도 글을 찾을 수 없어요."));
    }

    private Transfer loadOwned(long userId, long id) {
        Transfer t = load(id);
        if (t.getAuthorId() != userId) {
            throw new ApiException(ErrorCode.FORBIDDEN, "내가 쓴 글만 바꿀 수 있어요.");
        }
        return t;
    }

    private static Specification<Transfer> spec(Bbox bbox, TransferStatus status) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("status"), status));
            if (bbox != null) {
                where.add(cb.between(root.get("lng"), bbox.minLng(), bbox.maxLng()));
                where.add(cb.between(root.get("lat"), bbox.minLat(), bbox.maxLat()));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }
}
