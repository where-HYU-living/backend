package com.chatatda.api.realdeal;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.YearMonth;

@Entity
@Table(name = "real_deals")
public class RealDeal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lawd_cd", nullable = false)
    private String lawdCd;

    @Enumerated(EnumType.STRING)
    @Column(name = "building_type", nullable = false)
    private RealDealType buildingType;

    @Column(nullable = false)
    private String dong;

    @Column(name = "area_m2", nullable = false)
    private double areaM2;

    @Column(nullable = false)
    private long deposit;

    @Column(nullable = false)
    private long rent;

    @Convert(converter = YearMonthIntegerConverter.class)
    @Column(name = "contract_ym", nullable = false)
    private YearMonth contractYm;

    private Integer floor;

    @Column(name = "built_year")
    private Integer builtYear;

    @Column(name = "contract_type")
    private String contractType;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    protected RealDeal() {
    }

    public RealDeal(String lawdCd, RealDealType buildingType, String dong, double areaM2, long deposit, long rent,
                    YearMonth contractYm, Integer floor, Integer builtYear, String contractType, Instant ingestedAt) {
        this.lawdCd = lawdCd;
        this.buildingType = buildingType;
        this.dong = dong;
        this.areaM2 = areaM2;
        this.deposit = deposit;
        this.rent = rent;
        this.contractYm = contractYm;
        this.floor = floor;
        this.builtYear = builtYear;
        this.contractType = contractType;
        this.ingestedAt = ingestedAt;
    }

    public Long getId() {
        return id;
    }

    public String getLawdCd() {
        return lawdCd;
    }

    public RealDealType getBuildingType() {
        return buildingType;
    }

    public String getDong() {
        return dong;
    }

    public double getAreaM2() {
        return areaM2;
    }

    public long getDeposit() {
        return deposit;
    }

    public long getRent() {
        return rent;
    }

    public YearMonth getContractYm() {
        return contractYm;
    }

    public Integer getFloor() {
        return floor;
    }

    public Integer getBuiltYear() {
        return builtYear;
    }

    public String getContractType() {
        return contractType;
    }

    public Instant getIngestedAt() {
        return ingestedAt;
    }
}
