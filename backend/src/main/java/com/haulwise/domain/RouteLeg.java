package com.haulwise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;

/**
 * A stored, directed road distance between two warehouses (both directions are stored separately).
 */
@Entity
@Table(name = "route_leg", uniqueConstraints = @UniqueConstraint(
        name = "uk_route_leg_from_to", columnNames = {"from_id", "to_id"}))
public class RouteLeg {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_id", nullable = false)
    private Warehouse fromWarehouse;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_id", nullable = false)
    private Warehouse toWarehouse;

    @Column(nullable = false)
    private double distanceKm;

    @Column(nullable = false)
    private double durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private LegSource source;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    protected RouteLeg() {
    }

    public RouteLeg(Warehouse fromWarehouse, Warehouse toWarehouse, double distanceKm, double durationMinutes,
                    LegSource source, LocalDateTime updatedAt) {
        this.fromWarehouse = fromWarehouse;
        this.toWarehouse = toWarehouse;
        this.distanceKm = distanceKm;
        this.durationMinutes = durationMinutes;
        this.source = source;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public Warehouse getFromWarehouse() {
        return fromWarehouse;
    }

    public Warehouse getToWarehouse() {
        return toWarehouse;
    }

    public double getDistanceKm() {
        return distanceKm;
    }

    public double getDurationMinutes() {
        return durationMinutes;
    }

    public LegSource getSource() {
        return source;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Replaces the measured values, e.g. when an OSRM refresh overwrites a seeded leg.
     */
    public void update(double distanceKm, double durationMinutes, LegSource source, LocalDateTime updatedAt) {
        this.distanceKm = distanceKm;
        this.durationMinutes = durationMinutes;
        this.source = source;
        this.updatedAt = updatedAt;
    }
}
