package com.haulwise.solver.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

/**
 * One delivery stop of a truck: all shipments of the truck that go to the same destination warehouse
 * (ARCHITECTURE 6.1 step 2).
 *
 * @param warehouseId destination warehouse
 * @param shipmentIds shipments unloaded here, in {@code BinPacker.SHIPMENT_ORDER}; not empty, copied, unmodifiable
 * @param dropPallets pallets unloaded here, &gt;= 1
 * @param dropWeightKg weight unloaded here, &gt;= 1
 * @param maxPriority most important priority among the shipments
 * @param pinned whether any of the shipments is pinned first
 * @param earliestDeadline earliest deadline among the shipments, or {@code null} when none has one
 */
public record Stop(
        long warehouseId,
        List<Long> shipmentIds,
        int dropPallets,
        int dropWeightKg,
        Priority maxPriority,
        boolean pinned,
        LocalDateTime earliestDeadline) {

    public Stop {
        shipmentIds = List.copyOf(shipmentIds);
        if (shipmentIds.isEmpty()) {
            throw new IllegalArgumentException("A stop needs at least one shipment");
        }
        if (dropPallets < 1) {
            throw new IllegalArgumentException("dropPallets must be >= 1, was " + dropPallets);
        }
        if (dropWeightKg < 1) {
            throw new IllegalArgumentException("dropWeightKg must be >= 1, was " + dropWeightKg);
        }
        Objects.requireNonNull(maxPriority, "maxPriority");
    }

    /** The same stop with its pin cleared (used when another stop wins a pin conflict). */
    public Stop unpinned() {
        return new Stop(warehouseId, shipmentIds, dropPallets, dropWeightKg, maxPriority, false, earliestDeadline);
    }

    /** The stop as input to the cost model. */
    public RouteStop toRouteStop() {
        return new RouteStop(warehouseId, dropWeightKg);
    }
}
