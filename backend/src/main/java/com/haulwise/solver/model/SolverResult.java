package com.haulwise.solver.model;

import java.util.List;
import java.util.Objects;

/**
 * Output of the {@code Planner}: both variants over the same truck assignment (D-03), the cost of priority,
 * and what the variants share.
 *
 * @param optimal cost-only stop orders
 * @param priority priority-respecting stop orders (pinned stop first)
 * @param delta {@code priority - optimal}, never negative in cost (D-14)
 * @param unassigned shipments no truck could take, in the packer's shipment order; copied, unmodifiable
 * @param warnings plan-wide warnings (PIN_CONFLICT), in truck order; copied, unmodifiable
 */
public record SolverResult(
        PlanVariant optimal, PlanVariant priority, Delta delta, List<Unassigned> unassigned, List<Warning> warnings) {

    public SolverResult {
        Objects.requireNonNull(optimal, "optimal");
        Objects.requireNonNull(priority, "priority");
        Objects.requireNonNull(delta, "delta");
        if (optimal.mode() != Mode.OPTIMAL || priority.mode() != Mode.PRIORITY) {
            throw new IllegalArgumentException("Expected an OPTIMAL and a PRIORITY variant, got "
                    + optimal.mode() + " and " + priority.mode());
        }
        unassigned = List.copyOf(unassigned);
        warnings = List.copyOf(warnings);
    }
}
