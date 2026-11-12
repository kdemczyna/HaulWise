package com.haulwise.solver.model;

import java.util.Objects;

/**
 * The cost of priority: PRIORITY variant totals minus OPTIMAL variant totals (ARCHITECTURE 6.1 step 7, 6.5).
 * Values are unrounded. {@code costPln} is never negative (D-14); the other values may be, e.g. a priority
 * order can be shorter but slower.
 *
 * @param distanceKm extra road kilometres
 * @param durationMinutes extra working minutes (driving + service; service is the same in both variants)
 * @param litres extra fuel
 * @param fuelCostPln extra fuel cost
 * @param driverCostPln extra driver cost
 * @param costPln extra total cost, {@code fuelCostPln + driverCostPln}
 * @param noExtraCost whether the difference is negligible (|cost| &lt; 0.50 PLN and |distance| &lt; 0.5 km)
 * @param sentence plain-language summary, not blank
 */
public record Delta(
        double distanceKm,
        double durationMinutes,
        double litres,
        double fuelCostPln,
        double driverCostPln,
        double costPln,
        boolean noExtraCost,
        String sentence) {

    public Delta {
        Objects.requireNonNull(sentence, "sentence");
        if (sentence.isBlank()) {
            throw new IllegalArgumentException("sentence must not be blank");
        }
    }
}
