package com.haulwise.solver;

import com.haulwise.solver.model.ShipmentItem;
import com.haulwise.solver.model.Stop;
import com.haulwise.solver.model.TruckLoad;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Turns the shipments of one truck into delivery stops, one per destination warehouse (ARCHITECTURE 6.1
 * step 2): drops are summed, the stop takes the most important priority and the earliest deadline of its
 * shipments, and it is pinned when any of its shipments is pinned first.
 * <p>
 * Stops are returned in the order of their most important shipment by {@link BinPacker#SHIPMENT_ORDER}
 * (shipment ids inside a stop too). When a truck has more than one pinned destination, the one to keep is
 * given by {@link #pinnedWinner(TruckLoad)} - the destination of the first <em>pinned</em> shipment, which
 * is not necessarily the first pinned stop in the list (a stop can rank early because of an unpinned
 * URGENT shipment). Clearing the other pins and reporting {@code PIN_CONFLICT} is done by the priority
 * handling (T-11), not here. Results do not depend on the order of the shipments in the load.
 * Stateless and thread-safe.
 */
public final class StopGrouper {

    /**
     * The destination whose pin wins on this truck (ARCHITECTURE 6.1 step 2): that of the first pinned
     * shipment in {@link BinPacker#SHIPMENT_ORDER}, or empty when no shipment is pinned.
     */
    public OptionalLong pinnedWinner(TruckLoad load) {
        Objects.requireNonNull(load, "load");
        return load.shipments().stream()
                .filter(ShipmentItem::pinnedFirst)
                .min(BinPacker.SHIPMENT_ORDER)
                .map(shipment -> OptionalLong.of(shipment.destinationId()))
                .orElse(OptionalLong.empty());
    }

    public List<Stop> group(TruckLoad load) {
        Objects.requireNonNull(load, "load");
        Map<Long, List<ShipmentItem>> byDestination = new LinkedHashMap<>();
        load.shipments().stream()
                .sorted(BinPacker.SHIPMENT_ORDER)
                .forEach(shipment -> byDestination
                        .computeIfAbsent(shipment.destinationId(), id -> new ArrayList<>())
                        .add(shipment));
        return byDestination.entrySet().stream()
                .map(entry -> toStop(entry.getKey(), entry.getValue()))
                .toList();
    }

    private static Stop toStop(long warehouseId, List<ShipmentItem> shipments) {
        return new Stop(
                warehouseId,
                shipments.stream().map(ShipmentItem::id).toList(),
                shipments.stream().mapToInt(ShipmentItem::pallets).sum(),
                shipments.stream().mapToInt(ShipmentItem::weightKg).sum(),
                shipments.stream().map(ShipmentItem::priority).min(Comparator.naturalOrder()).orElseThrow(),
                shipments.stream().anyMatch(ShipmentItem::pinnedFirst),
                shipments.stream()
                        .map(ShipmentItem::deadline)
                        .filter(Objects::nonNull)
                        .min(Comparator.<LocalDateTime>naturalOrder())
                        .orElse(null));
    }
}
