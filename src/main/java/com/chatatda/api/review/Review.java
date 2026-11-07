package com.chatatda.api.review;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "reviews")
public class Review {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "author_id", nullable = false)
    private long authorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false)
    private ReviewTargetType targetType;

    @Column(name = "target_id", nullable = false)
    private long targetId;

    @Column(nullable = false)
    private short rating;

    @Column(nullable = false)
    private short noise;

    @Column(nullable = false)
    private short light;

    @Column(nullable = false)
    private short bugs;

    @Column(nullable = false)
    private short landlord;

    @Column(name = "deposit_return", nullable = false)
    private short depositReturn;

    private String text;

    @Column(name = "pin_x")
    private Double pinX;

    @Column(name = "pin_y")
    private Double pinY;

    @Column(name = "pin_z")
    private Double pinZ;

    @Column(name = "floorplan_id")
    private Long floorplanId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Review() {
    }

    public Review(long authorId, ReviewTargetType targetType, long targetId, int rating, ReviewDtos.Scores scores,
                  String text, double[] pin, Long floorplanId, Instant createdAt) {
        this.authorId = authorId;
        this.targetType = targetType;
        this.targetId = targetId;
        this.rating = (short) rating;
        this.noise = scores.noise().shortValue();
        this.light = scores.light().shortValue();
        this.bugs = scores.bugs().shortValue();
        this.landlord = scores.landlord().shortValue();
        this.depositReturn = scores.depositReturn().shortValue();
        this.text = text;
        if (pin != null) {
            this.pinX = pin[0];
            this.pinY = pin[1];
            this.pinZ = pin[2];
        }
        this.floorplanId = floorplanId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public long getAuthorId() {
        return authorId;
    }

    public int getRating() {
        return rating;
    }

    public ReviewDtos.Scores getScores() {
        return new ReviewDtos.Scores((int) noise, (int) light, (int) bugs, (int) landlord, (int) depositReturn);
    }

    public String getText() {
        return text;
    }

    public double[] getPin() {
        return pinX == null ? null : new double[] {pinX, pinY, pinZ};
    }

    public Long getFloorplanId() {
        return floorplanId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
