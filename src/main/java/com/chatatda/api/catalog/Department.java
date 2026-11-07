package com.chatatda.api.catalog;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "departments")
public class Department {

    @Id
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String college;

    @ElementCollection
    @CollectionTable(name = "department_buildings", joinColumns = @JoinColumn(name = "department_id"))
    @OrderColumn(name = "position")
    @Column(name = "building_id", nullable = false)
    private List<Long> defaultBuildingIds = new ArrayList<>();

    protected Department() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCollege() {
        return college;
    }

    public List<Long> getDefaultBuildingIds() {
        return defaultBuildingIds;
    }
}
