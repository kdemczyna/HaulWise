package com.haulwise.solver;

import com.haulwise.solver.model.Mode;
import com.haulwise.solver.model.PackingResult;
import com.haulwise.solver.model.PinResolution;
import com.haulwise.solver.model.PlanVariant;
import com.haulwise.solver.model.RoutePlan;
import com.haulwise.solver.model.SequencedRoute;
import com.haulwise.solver.model.SolverInput;
import com.haulwise.solver.model.SolverResult;
import com.haulwise.solver.model.TruckLoad;
import com.haulwise.solver.model.Warning;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Runs the solver pipeline for one plan and produces both variants (ARCHITECTURE 6.1):
 * <ol>
 *   <li>{@link BinPacker} once - both variants share the assignment (D-03);</li>
 *   <li>per truck, {@link PinConflictResolver} groups the shipments into stops (at most one pinned) and
 *       reports PIN_CONFLICT; the same stops feed both variants;</li>
 *   <li>per truck, {@link StopSequencer} with {@link RouteObjective#COST} (OPTIMAL) and
 *       {@link PriorityObjective} (PRIORITY);</li>
 *   <li>optimal-dominance guard (D-14): if the PRIORITY order is strictly cheaper than the OPTIMAL one by
 *       unrounded {@code evaluation().totals().costPln()} (never the objective value), OPTIMAL uses it too,
 *       so the cost of priority is never negative;</li>
 *   <li>{@link DeltaCalculator}: PRIORITY minus OPTIMAL, with the sentence.</li>
 * </ol>
 * Arrivals stay offsets in minutes after departure; clock times and deadline checks are mapped by the plan
 * service (T-13). Stateless and thread-safe.
 */
public final class Planner {

    private final int maxBruteForceStops;
    private final BinPacker packer = new BinPacker();
    private final PinConflictResolver pinResolver = new PinConflictResolver(new StopGrouper());
    private final DeltaCalculator deltaCalculator = new DeltaCalculator();

    public Planner() {
        this(StopSequencer.DEFAULT_MAX_BRUTE_FORCE_STOPS);
    }

    /**
     * @param maxBruteForceStops largest stop count sequenced exhaustively, 0..{@value
     *        StopSequencer#MAX_BRUTE_FORCE_STOPS_LIMIT}
     */
    public Planner(int maxBruteForceStops) {
        if (maxBruteForceStops < 0 || maxBruteForceStops > StopSequencer.MAX_BRUTE_FORCE_STOPS_LIMIT) {
            throw new IllegalArgumentException("maxBruteForceStops must be 0.."
                    + StopSequencer.MAX_BRUTE_FORCE_STOPS_LIMIT + ", was " + maxBruteForceStops);
        }
        this.maxBruteForceStops = maxBruteForceStops;
    }

    /**
     * @throws IllegalArgumentException for duplicate shipment or truck ids, a shipment delivered to the depot
     *         or a warehouse unknown to the distance provider
     */
    public SolverResult plan(SolverInput input) {
        Objects.requireNonNull(input, "input");
        PackingResult packing = packer.pack(input.shipments(), input.trucks());
        StopSequencer sequencer = new StopSequencer(input.distances(), input.costParameters(), maxBruteForceStops);
        PriorityObjective priorityObjective = new PriorityObjective(input.priorityWeights());

        List<RoutePlan> optimalRoutes = new ArrayList<>();
        List<RoutePlan> priorityRoutes = new ArrayList<>();
        List<Warning> warnings = new ArrayList<>();
        for (TruckLoad load : packing.loads()) {
            PinResolution resolution = pinResolver.resolve(load);
            warnings.addAll(resolution.warnings());
            SequencedRoute byCost = sequencer.sequence(input.depotId(), resolution.stops(), load.truck(),
                    input.returnToDepot(), RouteObjective.COST);
            SequencedRoute byPriority = sequencer.sequence(input.depotId(), resolution.stops(), load.truck(),
                    input.returnToDepot(), priorityObjective);
            optimalRoutes.add(new RoutePlan(load, cheaper(byCost, byPriority)));
            priorityRoutes.add(new RoutePlan(load, byPriority));
        }

        PlanVariant optimal = new PlanVariant(Mode.OPTIMAL, optimalRoutes);
        PlanVariant priority = new PlanVariant(Mode.PRIORITY, priorityRoutes);
        return new SolverResult(optimal, priority, deltaCalculator.calculate(optimal, priority, input.cityNames()),
                packing.unassigned(), warnings);
    }

    /**
     * The optimal-dominance guard (D-14): the cost-ordered route, unless the priority-ordered one is strictly
     * cheaper by unrounded cost - then that order, with its pure cost as the objective value.
     */
    static SequencedRoute cheaper(SequencedRoute byCost, SequencedRoute byPriority) {
        double priorityCost = byPriority.evaluation().totals().costPln();
        if (priorityCost < byCost.evaluation().totals().costPln()) {
            return new SequencedRoute(byPriority.stops(), byPriority.evaluation(), priorityCost, byPriority.method());
        }
        return byCost;
    }
}
