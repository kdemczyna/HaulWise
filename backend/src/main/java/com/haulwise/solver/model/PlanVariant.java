package com.haulwise.solver.model;

import java.util.List;
import java.util.Objects;

/**
 * One plan variant: a route per loaded truck, in the bin packer's truck order (the same trucks in both
 * variants, D-03).
 *
 * @param mode which variant
 * @param routes one route per loaded truck; copied, unmodifiable
 */
public record PlanVariant(Mode mode, List<RoutePlan> routes) {

    public PlanVariant {
        Objects.requireNonNull(mode, "mode");
        routes = List.copyOf(routes);
    }

    /** Sum of the route totals; {@link Totals#ZERO} when no truck was loaded. */
    public Totals totals() {
        return routes.stream().map(RoutePlan::totals).reduce(Totals.ZERO, Totals::plus);
    }
}
