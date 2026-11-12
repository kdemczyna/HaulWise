package com.haulwise.solver.model;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Everything the {@code Planner} needs for one plan (ARCHITECTURE 6.1). Departure time and compliance rules
 * are not solver inputs yet: clock times and deadlines are mapped by the plan service (T-13), compliance is
 * T-22.
 *
 * @param depotId warehouse every truck starts at; no shipment may be delivered there
 * @param shipments shipments to plan, distinct ids; copied, unmodifiable
 * @param trucks selected trucks, distinct ids; copied, unmodifiable
 * @param returnToDepot whether each route ends with the empty leg back to the depot
 * @param distances road distances between the depot and all destinations
 * @param costParameters diesel price and service time
 * @param priorityWeights PLN per hour of URGENT / HIGH arrival time for the PRIORITY variant
 * @param cityNames warehouse id -> city used in the delta sentence; must name every shipment destination;
 *        copied, unmodifiable
 */
public record SolverInput(
        long depotId,
        List<ShipmentItem> shipments,
        List<TruckSpec> trucks,
        boolean returnToDepot,
        DistanceProvider distances,
        CostParameters costParameters,
        PriorityWeights priorityWeights,
        Map<Long, String> cityNames) {

    public SolverInput {
        shipments = List.copyOf(shipments);
        trucks = List.copyOf(trucks);
        Objects.requireNonNull(distances, "distances");
        Objects.requireNonNull(costParameters, "costParameters");
        Objects.requireNonNull(priorityWeights, "priorityWeights");
        Map<Long, String> names = Map.copyOf(cityNames);
        cityNames = names;
        List<Long> unnamed = shipments.stream()
                .map(ShipmentItem::destinationId)
                .filter(id -> !names.containsKey(id))
                .distinct()
                .toList();
        if (!unnamed.isEmpty()) {
            throw new IllegalArgumentException("cityNames has no entry for destination warehouses " + unnamed);
        }
    }
}
