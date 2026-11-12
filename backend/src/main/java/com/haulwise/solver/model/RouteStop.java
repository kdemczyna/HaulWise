package com.haulwise.solver.model;

/**
 * One stop of an ordered route as input to the cost model: where the truck goes and how much weight it
 * unloads there.
 *
 * @param warehouseId destination warehouse
 * @param dropWeightKg weight delivered at this stop, &gt;= 0
 */
public record RouteStop(long warehouseId, int dropWeightKg) {

    public RouteStop {
        if (dropWeightKg < 0) {
            throw new IllegalArgumentException("dropWeightKg must be >= 0, was " + dropWeightKg);
        }
    }
}
