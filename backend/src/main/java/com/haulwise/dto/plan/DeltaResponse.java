package com.haulwise.dto.plan;

import com.haulwise.solver.model.Delta;
import java.math.BigDecimal;

/**
 * The cost of priority: PRIORITY minus OPTIMAL totals, rounded like the numbers in the sentence (which the solver
 * builds with the same rounding). {@code costPln} is never negative (D-14); distance and duration may be.
 */
public record DeltaResponse(
        BigDecimal distanceKm,
        long durationMinutes,
        BigDecimal litres,
        BigDecimal fuelCostPln,
        BigDecimal driverCostPln,
        BigDecimal costPln,
        boolean noExtraCost,
        String sentence) {

    public static DeltaResponse from(Delta delta) {
        return new DeltaResponse(
                PlanNumbers.km(delta.distanceKm()),
                PlanNumbers.minutes(delta.durationMinutes()),
                PlanNumbers.litres(delta.litres()),
                PlanNumbers.pln(delta.fuelCostPln()),
                PlanNumbers.pln(delta.driverCostPln()),
                PlanNumbers.pln(delta.costPln()),
                delta.noExtraCost(),
                delta.sentence());
    }
}
