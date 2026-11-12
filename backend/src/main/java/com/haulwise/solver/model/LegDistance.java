package com.haulwise.solver.model;

import java.util.Objects;

/**
 * Road distance and driving time of one directed leg between two warehouses.
 */
public record LegDistance(double distanceKm, double durationMinutes, DistanceSource source) {

    /**
     * A warehouse to itself: zero by definition, so it is exact and never reported as an estimate.
     */
    public static final LegDistance ZERO = new LegDistance(0, 0, DistanceSource.SEEDED);

    public LegDistance {
        Checks.requireNonNegative(distanceKm, "distanceKm");
        Checks.requireNonNegative(durationMinutes, "durationMinutes");
        Objects.requireNonNull(source, "source");
    }

    public boolean isEstimated() {
        return source == DistanceSource.ESTIMATED;
    }
}
