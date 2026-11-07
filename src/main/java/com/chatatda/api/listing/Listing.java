package com.chatatda.api.listing;

import com.chatatda.api.pricing.Grade;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "listings")
public class Listing {

    @Id
    private Long id;

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

    @Column(name = "thumbnail_url")
    private String thumbnailUrl;

    @Column(name = "floorplan_id")
    private Long floorplanId;

    @Column(name = "built_year")
    private Integer builtYear;

    private String description;

    @Column(name = "converted_rent")
    private Long convertedRent;

    @Enumerated(EnumType.STRING)
    private Grade grade;

    @Column(name = "graded_at")
    private Instant gradedAt;

    @ElementCollection
    @CollectionTable(name = "listing_options", joinColumns = @JoinColumn(name = "listing_id"))
    @OrderColumn(name = "position")
    @Column(name = "option_name", nullable = false)
    private List<String> options = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "listing_photos", joinColumns = @JoinColumn(name = "listing_id"))
    @OrderColumn(name = "position")
    @Column(name = "url", nullable = false)
    private List<String> photos = new ArrayList<>();

    protected Listing() {
    }

    public void applyPrice(long convertedRent, Grade grade, Instant gradedAt) {
        this.convertedRent = convertedRent;
        this.grade = grade;
        this.gradedAt = gradedAt;
    }

    public Long getId() {
        return id;
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

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public Long getFloorplanId() {
        return floorplanId;
    }

    public Integer getBuiltYear() {
        return builtYear;
    }

    public String getDescription() {
        return description;
    }

    public Long getConvertedRent() {
        return convertedRent;
    }

    public Grade getGrade() {
        return grade;
    }

    public List<String> getOptions() {
        return options;
    }

    public List<String> getPhotos() {
        return photos;
    }
}
