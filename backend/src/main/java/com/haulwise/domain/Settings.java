package com.haulwise.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Global planning parameters. Exactly one row exists, always with id {@link #SINGLETON_ID}.
 */
@Entity
@Table(name = "settings")
public class Settings {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id = SINGLETON_ID;

    @Column(nullable = false)
    private double dieselPricePlnPerLitre;

    @Column(nullable = false)
    private double averageSpeedKmh;

    @Column(nullable = false)
    private double roadFactor;

    @Column(nullable = false)
    private int serviceMinutesPerStop;

    @Column(nullable = false)
    private double urgentWeightPlnPerHour;

    @Column(nullable = false)
    private double highWeightPlnPerHour;

    @Column(nullable = false)
    private boolean complianceEnabled;

    @Column(nullable = false)
    private int maxContinuousDrivingMinutes;

    @Column(nullable = false)
    private int breakMinutes;

    @Column(nullable = false)
    private int maxDailyDrivingMinutes;

    @Column(nullable = false)
    private int consolidationFillThresholdPct;

    protected Settings() {
    }

    /**
     * Settings with the documented defaults (ARCHITECTURE section 6.4).
     */
    public static Settings withDefaults() {
        Settings settings = new Settings();
        settings.dieselPricePlnPerLitre = 6.20;
        settings.averageSpeedKmh = 70;
        settings.roadFactor = 1.3;
        settings.serviceMinutesPerStop = 30;
        settings.urgentWeightPlnPerHour = 150;
        settings.highWeightPlnPerHour = 50;
        settings.complianceEnabled = true;
        settings.maxContinuousDrivingMinutes = 270;
        settings.breakMinutes = 45;
        settings.maxDailyDrivingMinutes = 540;
        settings.consolidationFillThresholdPct = 60;
        return settings;
    }

    public Long getId() {
        return id;
    }

    public double getDieselPricePlnPerLitre() {
        return dieselPricePlnPerLitre;
    }

    public void setDieselPricePlnPerLitre(double dieselPricePlnPerLitre) {
        this.dieselPricePlnPerLitre = dieselPricePlnPerLitre;
    }

    public double getAverageSpeedKmh() {
        return averageSpeedKmh;
    }

    public void setAverageSpeedKmh(double averageSpeedKmh) {
        this.averageSpeedKmh = averageSpeedKmh;
    }

    public double getRoadFactor() {
        return roadFactor;
    }

    public void setRoadFactor(double roadFactor) {
        this.roadFactor = roadFactor;
    }

    public int getServiceMinutesPerStop() {
        return serviceMinutesPerStop;
    }

    public void setServiceMinutesPerStop(int serviceMinutesPerStop) {
        this.serviceMinutesPerStop = serviceMinutesPerStop;
    }

    public double getUrgentWeightPlnPerHour() {
        return urgentWeightPlnPerHour;
    }

    public void setUrgentWeightPlnPerHour(double urgentWeightPlnPerHour) {
        this.urgentWeightPlnPerHour = urgentWeightPlnPerHour;
    }

    public double getHighWeightPlnPerHour() {
        return highWeightPlnPerHour;
    }

    public void setHighWeightPlnPerHour(double highWeightPlnPerHour) {
        this.highWeightPlnPerHour = highWeightPlnPerHour;
    }

    public boolean isComplianceEnabled() {
        return complianceEnabled;
    }

    public void setComplianceEnabled(boolean complianceEnabled) {
        this.complianceEnabled = complianceEnabled;
    }

    public int getMaxContinuousDrivingMinutes() {
        return maxContinuousDrivingMinutes;
    }

    public void setMaxContinuousDrivingMinutes(int maxContinuousDrivingMinutes) {
        this.maxContinuousDrivingMinutes = maxContinuousDrivingMinutes;
    }

    public int getBreakMinutes() {
        return breakMinutes;
    }

    public void setBreakMinutes(int breakMinutes) {
        this.breakMinutes = breakMinutes;
    }

    public int getMaxDailyDrivingMinutes() {
        return maxDailyDrivingMinutes;
    }

    public void setMaxDailyDrivingMinutes(int maxDailyDrivingMinutes) {
        this.maxDailyDrivingMinutes = maxDailyDrivingMinutes;
    }

    public int getConsolidationFillThresholdPct() {
        return consolidationFillThresholdPct;
    }

    public void setConsolidationFillThresholdPct(int consolidationFillThresholdPct) {
        this.consolidationFillThresholdPct = consolidationFillThresholdPct;
    }
}
