package com.haulwise.solver.model;

import java.util.Objects;

/**
 * Cost of one driven leg of a route. Values are unrounded; rounding happens only in DTO mapping. Minutes are
 * {@code double} throughout the solver (leg durations may be fractional estimates).
 *
 * @param fromId warehouse the leg starts at
 * @param toId warehouse the leg ends at (a stop, or the depot for the return leg)
 * @param distance road distance, driving time and their source
 * @param loadWeightKg weight on board while driving this leg
 * @param arrivalMinutes minutes after departure from the depot at which the truck arrives at {@code toId}
 * @param serviceMinutes unloading time at {@code toId} (0 for the return leg)
 * @param litres fuel used on this leg
 * @param fuelCostPln litres x diesel price
 * @param driverCostPln (driving + service minutes) / 60 x driver cost per hour
 */
public record Leg(
        long fromId,
        long toId,
        LegDistance distance,
        double loadWeightKg,
        double arrivalMinutes,
        double serviceMinutes,
        double litres,
        double fuelCostPln,
        double driverCostPln) {

    public Leg {
        Objects.requireNonNull(distance, "distance");
        Checks.requireNonNegative(loadWeightKg, "loadWeightKg");
        Checks.requireNonNegative(arrivalMinutes, "arrivalMinutes");
        Checks.requireNonNegative(serviceMinutes, "serviceMinutes");
        Checks.requireNonNegative(litres, "litres");
        Checks.requireNonNegative(fuelCostPln, "fuelCostPln");
        Checks.requireNonNegative(driverCostPln, "driverCostPln");
    }

    public double distanceKm() {
        return distance.distanceKm();
    }

    public double drivingMinutes() {
        return distance.durationMinutes();
    }

    public double costPln() {
        return fuelCostPln + driverCostPln;
    }

    public Totals totals() {
        return new Totals(distanceKm(), drivingMinutes(), serviceMinutes, litres, fuelCostPln, driverCostPln);
    }
}
