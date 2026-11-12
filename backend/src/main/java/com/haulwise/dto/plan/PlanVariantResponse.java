package com.haulwise.dto.plan;

import com.haulwise.solver.model.Mode;
import java.util.List;

/**
 * One plan variant.
 *
 * @param mode OPTIMAL (cost only) or PRIORITY (priorities and pins respected)
 * @param totals sum over the routes
 * @param deadlinesMissed number of stops reached after their deadline
 * @param routes one per loaded truck, in the same truck order in both variants
 * @param warnings variant-wide warnings; none are produced yet (per-truck warnings are in {@code routes[].warnings})
 */
public record PlanVariantResponse(
        Mode mode,
        TotalsResponse totals,
        int deadlinesMissed,
        List<RouteResponse> routes,
        List<WarningResponse> warnings) {

    public PlanVariantResponse {
        routes = List.copyOf(routes);
        warnings = List.copyOf(warnings);
    }
}
