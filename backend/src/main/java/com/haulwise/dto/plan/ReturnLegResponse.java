package com.haulwise.dto.plan;

import com.haulwise.solver.model.DistanceSource;
import com.haulwise.solver.model.Leg;
import java.math.BigDecimal;

/** The empty drive from the last stop back to the depot. */
public record ReturnLegResponse(
        BigDecimal distanceKm, long durationMinutes, BigDecimal litres, BigDecimal costPln, DistanceSource source) {

    public static ReturnLegResponse from(Leg leg) {
        return new ReturnLegResponse(PlanNumbers.km(leg.distanceKm()), PlanNumbers.minutes(leg.drivingMinutes()),
                PlanNumbers.litres(leg.litres()), PlanNumbers.pln(leg.costPln()), leg.distance().source());
    }
}
