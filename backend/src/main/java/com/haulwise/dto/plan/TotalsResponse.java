package com.haulwise.dto.plan;

import com.haulwise.solver.model.Totals;
import java.math.BigDecimal;

/**
 * Rounded totals of a route or a variant. Each value is rounded from the unrounded sum, so a variant total can
 * differ in the last digit from the sum of its rounded route totals.
 */
public record TotalsResponse(
        BigDecimal distanceKm,
        long drivingMinutes,
        long totalMinutes,
        BigDecimal litres,
        BigDecimal fuelCostPln,
        BigDecimal driverCostPln,
        BigDecimal costPln) {

    public static TotalsResponse from(Totals totals) {
        return new TotalsResponse(
                PlanNumbers.km(totals.distanceKm()),
                PlanNumbers.minutes(totals.drivingMinutes()),
                PlanNumbers.minutes(totals.totalMinutes()),
                PlanNumbers.litres(totals.litres()),
                PlanNumbers.pln(totals.fuelCostPln()),
                PlanNumbers.pln(totals.driverCostPln()),
                PlanNumbers.pln(totals.costPln()));
    }
}
