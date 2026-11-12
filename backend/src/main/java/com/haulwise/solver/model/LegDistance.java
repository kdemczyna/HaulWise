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
        requireNonNegative(distanceKm, "distanceKm");
        requireNonNegative(durationMinutes, "durationMinutes");
        Objects.requireNonNull(source, "source");
    }

    public boolean isEstimated() {
        return source == DistanceSource.ESTIMATED;
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be a finite number >= 0, was " + value);
        }
    }
}
