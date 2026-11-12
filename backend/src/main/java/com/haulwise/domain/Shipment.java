package com.haulwise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.Check;

/**
 * An atomic load moved from an origin warehouse to a destination warehouse.
 * Deadlines are local date-times in Europe/Warsaw (D-10).
 */
@Entity
@Table(name = "shipment", indexes = {
        @Index(name = "idx_shipment_origin", columnList = "origin_id"),
        @Index(name = "idx_shipment_priority", columnList = "priority")
})
@Check(name = "chk_shipment_origin_not_destination", constraints = "origin_id <> destination_id")
public class Shipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 40)
    private String reference;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "origin_id", nullable = false)
    private Warehouse origin;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destination_id", nullable = false)
    private Warehouse destination;

    @Column(nullable = false)
    private int pallets;

    @Column(nullable = false)
    private int weightKg;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority = Priority.NORMAL;

    private LocalDateTime deadline;

    @Column(nullable = false)
    private boolean pinnedFirst;

    protected Shipment() {
    }

    public Shipment(String reference, Warehouse origin, Warehouse destination, int pallets, int weightKg) {
        this.reference = reference;
        this.origin = origin;
        this.destination = destination;
        this.pallets = pallets;
        this.weightKg = weightKg;
    }

    public Long getId() {
        return id;
    }

    public String getReference() {
        return reference;
    }

    public void setReference(String reference) {
        this.reference = reference;
    }

    public Warehouse getOrigin() {
        return origin;
    }

    public void setOrigin(Warehouse origin) {
        this.origin = origin;
    }

    public Warehouse getDestination() {
        return destination;
    }

    public void setDestination(Warehouse destination) {
        this.destination = destination;
    }

    public int getPallets() {
        return pallets;
    }

    public void setPallets(int pallets) {
        this.pallets = pallets;
    }

    public int getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(int weightKg) {
        this.weightKg = weightKg;
    }

    public Priority getPriority() {
        return priority;
    }

    /** A {@code null} priority falls back to {@link Priority#NORMAL}. */
    public void setPriority(Priority priority) {
        this.priority = priority == null ? Priority.NORMAL : priority;
    }

    public LocalDateTime getDeadline() {
        return deadline;
    }

    public void setDeadline(LocalDateTime deadline) {
        this.deadline = deadline;
    }

    public boolean isPinnedFirst() {
        return pinnedFirst;
    }

    public void setPinnedFirst(boolean pinnedFirst) {
        this.pinnedFirst = pinnedFirst;
    }
}
