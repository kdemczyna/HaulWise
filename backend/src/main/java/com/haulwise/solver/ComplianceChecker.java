package com.haulwise.solver;

import com.haulwise.solver.model.ComplianceRules;
import com.haulwise.solver.model.ComplianceWarning;
import com.haulwise.solver.model.ComplianceWarning.BreakRequired;
import com.haulwise.solver.model.ComplianceWarning.DailyDrivingExceeded;
import com.haulwise.solver.model.DirectedPair;
import com.haulwise.solver.model.Leg;
import com.haulwise.solver.model.RouteEvaluation;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * EU driving-time check of one evaluated route (ARCHITECTURE 6.1 step 5, 6.4; Regulation (EC) 561/2006 Art. 6
 * and 7; T-22). Warnings only (D-09): the route's order, schedule and cost are never changed.
 * <ul>
 *   <li>Continuous driving is accumulated leg by leg in driving order, the return leg included. Service time at a
 *       stop is not a break, so it does not reset the counter. When the counter would pass
 *       {@link ComplianceRules#maxContinuousDrivingMinutes()} on a leg, one {@code BREAK_REQUIRED} names that leg
 *       and the moment the limit is reached; the driver is assumed to take the break then, so the counter restarts
 *       at 0 and a long leg or route can need several breaks. Reaching the limit exactly at the end of the route
 *       needs no break.</li>
 *   <li>Total driving above {@link ComplianceRules#maxDailyDrivingMinutes()}: one {@code DAILY_DRIVING_EXCEEDED}
 *       for the route.</li>
 * </ul>
 * Times are on the planned schedule, which contains no breaks: each break moment is the planned time at which the
 * limit is reached. Stateless and thread-safe.
 */
public final class ComplianceChecker {

    /** Driving within this many minutes of a limit counts as at the limit (absorbs floating-point noise). */
    static final double TOLERANCE_MINUTES = 1e-6;

    private final ComplianceRules rules;

    public ComplianceChecker(ComplianceRules rules) {
        this.rules = Objects.requireNonNull(rules, "rules");
    }

    /**
     * @param truckId the truck driving the route
     * @param evaluation the evaluated route; an empty route has no legs and no warnings
     * @return break warnings in driving order, then the daily warning if any
     */
    public List<ComplianceWarning> check(long truckId, RouteEvaluation evaluation) {
        Objects.requireNonNull(evaluation, "evaluation");
        List<ComplianceWarning> warnings = new ArrayList<>(breaks(truckId, evaluation.allLegs()));
        double drivingMinutes = evaluation.totals().drivingMinutes();
        if (drivingMinutes > rules.maxDailyDrivingMinutes() + TOLERANCE_MINUTES) {
            warnings.add(new DailyDrivingExceeded(truckId, drivingMinutes, rules.maxDailyDrivingMinutes()));
        }
        return warnings;
    }

    private List<ComplianceWarning> breaks(long truckId, List<Leg> legs) {
        int limit = rules.maxContinuousDrivingMinutes();
        List<ComplianceWarning> warnings = new ArrayList<>();
        double continuous = 0;
        for (Leg leg : legs) {
            double legStart = Math.max(0, leg.arrivalMinutes() - leg.drivingMinutes());
            double drivenOnLeg = 0;
            double remaining = leg.drivingMinutes();
            while (continuous + remaining > limit + TOLERANCE_MINUTES) {
                double untilLimit = limit - continuous;
                drivenOnLeg += untilLimit;
                remaining -= untilLimit;
                warnings.add(new BreakRequired(truckId, new DirectedPair(leg.fromId(), leg.toId()),
                        legStart + drivenOnLeg, limit, rules.breakMinutes()));
                continuous = 0;
            }
            continuous += remaining;
        }
        return warnings;
    }
}
