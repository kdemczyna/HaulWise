package com.haulwise.solver;

import com.haulwise.solver.model.PinResolution;
import com.haulwise.solver.model.ShipmentItem;
import com.haulwise.solver.model.Stop;
import com.haulwise.solver.model.TruckLoad;
import com.haulwise.solver.model.Warning;
import com.haulwise.solver.model.WarningCode;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Groups a truck's shipments into stops and makes sure at most one of them is pinned (ARCHITECTURE 6.1
 * step 2, T-11). When several destinations are pinned, the one of {@link StopGrouper#pinnedWinner} keeps its
 * pin, the others are unpinned and a {@link WarningCode#PIN_CONFLICT} warning lists the shipments whose pin
 * was dropped. Pinned shipments that share the winning destination are not a conflict.
 * Stateless and thread-safe.
 */
public final class PinConflictResolver {

    /** Neutral solver text; the user-facing sentence with plate, city and references is built by the Planner (T-13). */
    static final String CONFLICT_MESSAGE = "Only one stop per truck can be pinned first; "
            + "the other pinned deliveries on this truck are ordered by priority.";

    private final StopGrouper grouper;

    public PinConflictResolver(StopGrouper grouper) {
        this.grouper = Objects.requireNonNull(grouper, "grouper");
    }

    public PinResolution resolve(TruckLoad load) {
        List<Stop> stops = grouper.group(load);
        OptionalLong winner = grouper.pinnedWinner(load);
        if (winner.isEmpty() || stops.stream().filter(Stop::pinned).count() <= 1) {
            return new PinResolution(stops, List.of());
        }
        long keep = winner.getAsLong();
        List<Stop> resolved = stops.stream()
                .map(stop -> stop.pinned() && stop.warehouseId() != keep ? stop.unpinned() : stop)
                .toList();
        List<Long> dropped = load.shipments().stream()
                .filter(shipment -> shipment.pinnedFirst() && shipment.destinationId() != keep)
                .sorted(BinPacker.SHIPMENT_ORDER)
                .map(ShipmentItem::id)
                .toList();
        Warning warning = new Warning(WarningCode.PIN_CONFLICT, CONFLICT_MESSAGE, load.truck().id(), dropped);
        return new PinResolution(resolved, List.of(warning));
    }
}
