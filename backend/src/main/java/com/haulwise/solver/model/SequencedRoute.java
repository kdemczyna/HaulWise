package com.haulwise.solver.model;

import java.util.List;
import java.util.Objects;

/**
 * The stop order chosen for one truck, with its cost breakdown.
 *
 * @param stops stops in delivery order; copied, unmodifiable
 * @param evaluation fuel, time and cost of that order (always the pure cost model, never a penalty)
 * @param objectiveValue value of the objective the order was chosen by (equals the cost in OPTIMAL mode)
 * @param method how the order was found
 */
public record SequencedRoute(
        List<Stop> stops, RouteEvaluation evaluation, double objectiveValue, SequencingMethod method) {

    public SequencedRoute {
        stops = List.copyOf(stops);
        Objects.requireNonNull(evaluation, "evaluation");
        Objects.requireNonNull(method, "method");
    }

    /** Destination warehouse ids in delivery order. */
    public List<Long> warehouseIds() {
        return stops.stream().map(Stop::warehouseId).toList();
    }
}
