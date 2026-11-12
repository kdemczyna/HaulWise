package com.haulwise.solver.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable cost breakdown of an ordered route: one leg per stop (in delivery order), plus the return leg
 * when the truck goes back to the depot. {@code totals} is the sum of all legs.
 */
public record RouteEvaluation(List<Leg> stopLegs, Optional<Leg> returnLeg, Totals totals) {

    public RouteEvaluation {
        stopLegs = List.copyOf(stopLegs);
        Objects.requireNonNull(returnLeg, "returnLeg");
        Objects.requireNonNull(totals, "totals");
    }

    /** Stop legs followed by the return leg, if any. */
    public List<Leg> allLegs() {
        if (returnLeg.isEmpty()) {
            return stopLegs;
        }
        List<Leg> legs = new ArrayList<>(stopLegs);
        legs.add(returnLeg.get());
        return List.copyOf(legs);
    }
}
