package com.haulwise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Locale;

/**
 * A truck of the fleet with its capacity and fuel/driver cost parameters, its home depot (T-32: a plan or a
 * recommendation only uses trucks based at its depot) and whether it is available (T-33: {@code false} while it
 * is on route after a dispatched plan).
 */
@Entity
@Table(name = "truck", indexes = @Index(name = "idx_truck_home_warehouse", columnList = "home_warehouse_id"))
public class Truck {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Registration plate, always stored normalised (see {@link #normalisePlate(String)}). */
    @Column(nullable = false, unique = true, length = 15)
    private String plate;

    @Column(nullable = false)
    private int capacityPallets;

    @Column(nullable = false)
    private int payloadKg;

    @Column(name = "base_consumption_l_per_100km", nullable = false)
    private double baseConsumptionLPer100Km;

    @Column(name = "extra_consumption_full_load_l_per_100km", nullable = false)
    private double extraConsumptionFullLoadLPer100Km;

    @Column(nullable = false)
    private double driverCostPlnPerHour;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "home_warehouse_id", nullable = false)
    private Warehouse homeWarehouse;

    @Column(nullable = false)
    private boolean available = true;

    protected Truck() {
    }

    public Truck(String plate, int capacityPallets, int payloadKg, double baseConsumptionLPer100Km,
                 double extraConsumptionFullLoadLPer100Km, double driverCostPlnPerHour, Warehouse homeWarehouse) {
        setPlate(plate);
        this.capacityPallets = capacityPallets;
        this.payloadKg = payloadKg;
        this.baseConsumptionLPer100Km = baseConsumptionLPer100Km;
        this.extraConsumptionFullLoadLPer100Km = extraConsumptionFullLoadLPer100Km;
        this.driverCostPlnPerHour = driverCostPlnPerHour;
        this.homeWarehouse = homeWarehouse;
    }

    /**
     * Normalises a plate the way it is stored (whitespace runs, incl. no-break spaces, collapsed to one
     * space, stripped, upper-case), so lookups and uniqueness checks agree with it.
     */
    public static String normalisePlate(String plate) {
        return plate == null ? null : plate.replaceAll("(?U)\\s+", " ").strip().toUpperCase(Locale.ROOT);
    }

    public Long getId() {
        return id;
    }

    public String getPlate() {
        return plate;
    }

    public void setPlate(String plate) {
        this.plate = normalisePlate(plate);
    }

    public int getCapacityPallets() {
        return capacityPallets;
    }

    public void setCapacityPallets(int capacityPallets) {
        this.capacityPallets = capacityPallets;
    }

    public int getPayloadKg() {
        return payloadKg;
    }

    public void setPayloadKg(int payloadKg) {
        this.payloadKg = payloadKg;
    }

    public double getBaseConsumptionLPer100Km() {
        return baseConsumptionLPer100Km;
    }

    public void setBaseConsumptionLPer100Km(double baseConsumptionLPer100Km) {
        this.baseConsumptionLPer100Km = baseConsumptionLPer100Km;
    }

    public double getExtraConsumptionFullLoadLPer100Km() {
        return extraConsumptionFullLoadLPer100Km;
    }

    public void setExtraConsumptionFullLoadLPer100Km(double extraConsumptionFullLoadLPer100Km) {
        this.extraConsumptionFullLoadLPer100Km = extraConsumptionFullLoadLPer100Km;
    }

    public double getDriverCostPlnPerHour() {
        return driverCostPlnPerHour;
    }

    public void setDriverCostPlnPerHour(double driverCostPlnPerHour) {
        this.driverCostPlnPerHour = driverCostPlnPerHour;
    }

    public Warehouse getHomeWarehouse() {
        return homeWarehouse;
    }

    public void setHomeWarehouse(Warehouse homeWarehouse) {
        this.homeWarehouse = homeWarehouse;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }
}
