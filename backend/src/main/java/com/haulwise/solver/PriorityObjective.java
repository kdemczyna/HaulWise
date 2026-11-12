package com.haulwise.solver;

import com.haulwise.solver.model.PriorityWeights;
import com.haulwise.solver.model.RouteEvaluation;
import com.haulwise.solver.model.Stop;
import java.util.List;
import java.util.Objects;

/**
 * The PRIORITY mode objective (ARCHITECTURE 6.3, D-13):
 * <pre>
 *   score = cost + sum over stops i of weight(stop_i.maxPriority) x arrivalMinutes_i / 60
 * </pre>
 * where {@code arrivalMinutes_i} is the truck's arrival at stop i in minutes after leaving the depot (the
 * driving time of all legs up to it plus the service time of the earlier stops, not its own - section 6.2),
 * and the weight is in PLN per hour (URGENT and HIGH from the settings, NORMAL 0). The pinned stop is fixed
 * first ({@link #pinnedStopFirst()}); pin conflicts must be resolved beforehand by {@link PinConflictResolver}.
 * <p>
 * The penalty only chooses the order: the {@link RouteEvaluation} the sequencer reports is pure cost.
 */
public final class PriorityObjective implements RouteObjective {

    private static final double MINUTES_PER_HOUR = 60.0;

    private final PriorityWeights weights;

    public PriorityObjective(PriorityWeights weights) {
        this.weights = Objects.requireNonNull(weights, "weights");
    }

    @Override
    public double score(List<Stop> stops, RouteEvaluation evaluation) {
        return evaluation.totals().costPln() + penaltyPln(stops, evaluation);
    }

    /** The priority term alone: sum of weight x arrival hours over the stops, in PLN. */
    public double penaltyPln(List<Stop> stops, RouteEvaluation evaluation) {
        if (stops.size() != evaluation.stopLegs().size()) {
            throw new IllegalArgumentException("Expected one leg per stop, got " + evaluation.stopLegs().size()
                    + " legs for " + stops.size() + " stops");
        }
        double penalty = 0;
        for (int i = 0; i < stops.size(); i++) {
            double arrivalHours = evaluation.stopLegs().get(i).arrivalMinutes() / MINUTES_PER_HOUR;
            penalty += weights.plnPerHour(stops.get(i).maxPriority()) * arrivalHours;
        }
        return penalty;
    }

    @Override
    public boolean pinnedStopFirst() {
        return true;
    }
}
