package com.chatatda.api.user;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false, unique = true)
    private String nickname;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "department_id")
    private Long departmentId;

    @Column(name = "rent_cap")
    private Long rentCap;

    @Column(name = "deposit_cap")
    private Long depositCap;

    @ElementCollection
    @CollectionTable(name = "user_buildings", joinColumns = @JoinColumn(name = "user_id"))
    @OrderColumn(name = "position")
    @Column(name = "building_id", nullable = false)
    private List<Long> buildingIds = new ArrayList<>();

    protected User() {
    }

    public User(String email, String nickname, Instant createdAt) {
        this.email = email;
        this.nickname = nickname;
        this.createdAt = createdAt;
    }

    public void changeDepartment(Long departmentId) {
        this.departmentId = departmentId;
    }

    public void changeBuildings(List<Long> buildingIds) {
        this.buildingIds.clear();
        this.buildingIds.addAll(buildingIds);
    }

    public void changeBudget(Budget budget) {
        this.rentCap = budget.rentCap();
        this.depositCap = budget.depositCap();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getNickname() {
        return nickname;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Long getDepartmentId() {
        return departmentId;
    }

    public List<Long> getBuildingIds() {
        return buildingIds;
    }

    public Budget getBudget() {
        if (rentCap == null && depositCap == null) {
            return null;
        }
        return new Budget(rentCap, depositCap);
    }
}
