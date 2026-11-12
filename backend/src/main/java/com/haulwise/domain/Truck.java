package com.haulwise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Locale;

/**
 * A truck of the fleet with its capacity and fuel/driver cost parameters.
 */
@Entity
@Table(name = "truck")
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

    protected Truck() {
    }

    public Truck(String plate, int capacityPallets, int payloadKg, double baseConsumptionLPer100Km,
                 double extraConsumptionFullLoadLPer100Km, double driverCostPlnPerHour) {
        setPlate(plate);
        this.capacityPallets = capacityPallets;
        this.payloadKg = payloadKg;
        this.baseConsumptionLPer100Km = baseConsumptionLPer100Km;
        this.extraConsumptionFullLoadLPer100Km = extraConsumptionFullLoadLPer100Km;
        this.driverCostPlnPerHour = driverCostPlnPerHour;
    }

    /**
     * Normalises a plate the way it is stored (trimmed, inner whitespace collapsed to one space,
     * upper-case), so lookups and uniqueness checks agree with it.
     */
    public static String normalisePlate(String plate) {
        return plate == null ? null : plate.trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT);
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
}
