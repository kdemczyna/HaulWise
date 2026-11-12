package com.haulwise.solver.model;

/**
 * How {@code StopSequencer} found a stop order.
 */
public enum SequencingMethod {
    /** Every permutation was scored: the order is optimal for the objective. */
    BRUTE_FORCE,
    /** Nearest neighbour by distance, improved by 2-opt on the objective: good, not guaranteed optimal. */
    NEAREST_NEIGHBOUR_TWO_OPT
}
