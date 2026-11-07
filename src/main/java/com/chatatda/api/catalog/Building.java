package com.chatatda.api.catalog;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "buildings")
public class Building {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BuildingType type;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    protected Building() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BuildingType getType() {
        return type;
    }

    public double getLat() {
        return lat;
    }

    public double getLng() {
        return lng;
    }
}
