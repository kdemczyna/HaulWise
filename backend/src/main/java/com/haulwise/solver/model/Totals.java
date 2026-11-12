package com.haulwise.solver.model;

/**
 * Summed distance, time, fuel and cost of one or more legs or routes. Values are unrounded; minutes
 * are {@code double} throughout the solver.
 */
public record Totals(
        double distanceKm,
        double drivingMinutes,
        double serviceMinutes,
        double litres,
        double fuelCostPln,
        double driverCostPln) {

    public static final Totals ZERO = new Totals(0, 0, 0, 0, 0, 0);

    public double totalMinutes() {
        return drivingMinutes + serviceMinutes;
    }

    public double costPln() {
        return fuelCostPln + driverCostPln;
    }

    public Totals plus(Totals other) {
        return new Totals(
                distanceKm + other.distanceKm,
                drivingMinutes + other.drivingMinutes,
                serviceMinutes + other.serviceMinutes,
                litres + other.litres,
                fuelCostPln + other.fuelCostPln,
                driverCostPln + other.driverCostPln);
    }
}
