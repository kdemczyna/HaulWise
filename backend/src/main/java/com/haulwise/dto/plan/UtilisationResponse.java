package com.haulwise.dto.plan;

import com.haulwise.solver.model.TruckLoad;
import java.math.BigDecimal;

/** How full a truck leaves the depot, by pallets and by weight. */
public record UtilisationResponse(
        int pallets, int capacityPallets, int weightKg, int payloadKg, BigDecimal palletsPct, BigDecimal weightPct) {

    public static UtilisationResponse from(TruckLoad load) {
        return new UtilisationResponse(load.pallets(), load.truck().capacityPallets(), load.weightKg(),
                load.truck().payloadKg(), PlanNumbers.percent(load.palletsPct()),
                PlanNumbers.percent(load.weightPct()));
    }
}
