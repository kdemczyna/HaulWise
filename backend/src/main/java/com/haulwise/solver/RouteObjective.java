package com.haulwise.solver;

import com.haulwise.solver.model.RouteEvaluation;
import com.haulwise.solver.model.Stop;
import java.util.List;

/**
 * What {@link StopSequencer} minimises when it chooses a stop order (ARCHITECTURE 6.3). The sequencer
 * scores every candidate order with {@link RouteEvaluator} and passes the evaluation here, so an objective
 * can combine the cost with anything it reads from the stops (priority, arrival offsets of the legs).
 * <p>
 * {@link #COST} is the OPTIMAL mode. The PRIORITY mode (T-11, {@code PriorityObjective}) adds a penalty
 * term and fixes the pinned stop first via {@link #pinnedStopFirst()}.
 */
@FunctionalInterface
public interface RouteObjective {

    /** Total route cost in PLN; priorities and pins are ignored. */
    RouteObjective COST = (stops, evaluation) -> evaluation.totals().costPln();

    /**
     * @param stops the candidate order
     * @param evaluation fuel, time and cost of that order; {@code stopLegs().get(i)} is the leg to {@code stops.get(i)}
     * @return the value to minimise, a finite number
     */
    double score(List<Stop> stops, RouteEvaluation evaluation);

    /** Whether the pinned stop (at most one) must be visited first. */
    default boolean pinnedStopFirst() {
        return false;
    }
}
