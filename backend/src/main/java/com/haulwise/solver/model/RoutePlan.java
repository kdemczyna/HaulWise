package com.haulwise.solver.model;

import java.util.Objects;

/**
 * One truck's route in a plan variant: what the truck carries (shared by both variants, D-03) and the stop
 * order chosen for the variant.
 *
 * @param load the truck and its shipments
 * @param route stops in delivery order with their unrounded cost breakdown (arrival offsets in minutes)
 */
public record RoutePlan(TruckLoad load, SequencedRoute route) {

    public RoutePlan {
        Objects.requireNonNull(load, "load");
        Objects.requireNonNull(route, "route");
    }

    public TruckSpec truck() {
        return load.truck();
    }

    public Totals totals() {
        return route.evaluation().totals();
    }
}
