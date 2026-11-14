package com.haulwise.solver.model;

import java.util.List;
import java.util.Objects;

/**
 * One truck's route in a plan variant: what the truck carries (shared by both variants, D-03), the stop order
 * chosen for the variant and the driving-time warnings of that order.
 *
 * @param load the truck and its shipments
 * @param route stops in delivery order with their unrounded cost breakdown (arrival offsets in minutes)
 * @param complianceWarnings EU driving-time warnings of this route (T-22), empty when compliance checking is off;
 *        copied, unmodifiable
 */
public record RoutePlan(TruckLoad load, SequencedRoute route, List<ComplianceWarning> complianceWarnings) {

    public RoutePlan {
        Objects.requireNonNull(load, "load");
        Objects.requireNonNull(route, "route");
        complianceWarnings = List.copyOf(complianceWarnings);
    }

    /** A route without compliance warnings. */
    public RoutePlan(TruckLoad load, SequencedRoute route) {
        this(load, route, List.of());
    }

    public TruckSpec truck() {
        return load.truck();
    }

    public Totals totals() {
        return route.evaluation().totals();
    }
}
