package com.chatatda.api.transfer;

import com.chatatda.api.listing.RoomType;
import com.chatatda.api.pricing.Grade;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 낙관적 잠금 (V12): 글 고치기와 양도 완료가 동시에 오면 늦은 쪽이 409 를 받는다 (예전엔 상태가 조용히 되돌아갔다) */
    @jakarta.persistence.Version
    private long version;

    @Column(name = "author_id", nullable = false)
    private long authorId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String address;

    @Column(nullable = false)
    private String dong;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Column(nullable = false)
    private long deposit;

    @Column(nullable = false)
    private long rent;

    @Column(name = "mgmt_fee")
    private Long mgmtFee;

    @Column(name = "area_m2", nullable = false)
    private double areaM2;

    private Integer floor;

    @Enumerated(EnumType.STRING)
    @Column(name = "room_type", nullable = false)
    private RoomType roomType;

    @Column(name = "remaining_months", nullable = false)
    private int remainingMonths;

    @Column(name = "available_from")
    private LocalDate availableFrom;

    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransferStatus status;

    @Column(name = "converted_rent")
    private Long convertedRent;

    @Enumerated(EnumType.STRING)
    private Grade grade;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @ElementCollection
    @CollectionTable(name = "transfer_photos", joinColumns = @JoinColumn(name = "transfer_id"))
    @OrderColumn(name = "position")
    @Column(name = "url")
    private List<String> photos = new ArrayList<>();

    protected Transfer() {
    }

    public Transfer(long authorId, Instant now) {
        this.authorId = authorId;
        this.status = TransferStatus.OPEN;
        this.createdAt = now;
        this.updatedAt = now;
    }

    void apply(TransferDtos.TransferRequest r, Instant now) {
        this.title = r.title().trim();
        this.address = r.address().trim();
        this.dong = r.dong().trim();
        this.lat = r.lat();
        this.lng = r.lng();
        this.deposit = r.deposit();
        this.rent = r.rent();
        this.mgmtFee = r.mgmtFee();
        this.areaM2 = r.areaM2();
        this.floor = r.floor();
        this.roomType = r.roomType();
        this.remainingMonths = r.remainingMonths();
        this.availableFrom = r.availableFrom();
        this.description = r.description();
        this.photos.clear();
        if (r.photoUrls() != null) {
            this.photos.addAll(r.photoUrls());
        }
        this.updatedAt = now;
    }

    void applyPrice(long convertedRent, Grade grade) {
        this.convertedRent = convertedRent;
        this.grade = grade;
    }

    void changeStatus(TransferStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public long getAuthorId() {
        return authorId;
    }

    public String getTitle() {
        return title;
    }

    public String getAddress() {
        return address;
    }

    public String getDong() {
        return dong;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }

    public long getDeposit() {
        return deposit;
    }

    public long getRent() {
        return rent;
    }

    public Long getMgmtFee() {
        return mgmtFee;
    }

    public double getAreaM2() {
        return areaM2;
    }

    public Integer getFloor() {
        return floor;
    }

    public RoomType getRoomType() {
        return roomType;
    }

    public int getRemainingMonths() {
        return remainingMonths;
    }

    public LocalDate getAvailableFrom() {
        return availableFrom;
    }

    public String getDescription() {
        return description;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public Long getConvertedRent() {
        return convertedRent;
    }

    public Grade getGrade() {
        return grade;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public List<String> getPhotos() {
        return photos;
    }
}
