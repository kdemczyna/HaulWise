package com.haulwise.solver.model;

/**
 * Source of road distances and driving times between warehouses, as seen by the solver.
 */
public interface DistanceProvider {

    /**
     * Returns the directed leg from one warehouse to another; a warehouse to itself is {@link LegDistance#ZERO}.
     *
     * @throws IllegalArgumentException if either warehouse is unknown to this provider
     */
    LegDistance distance(long fromId, long toId);
}
