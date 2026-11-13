package com.haulwise.dto.plan;

import com.haulwise.solver.model.Mode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Result of {@code POST /api/plans/{id}/dispatch} (T-33): what was recorded as sent.
 *
 * @param mode the variant that was sent
 * @param dispatchedAt when it was sent; also the plan's {@code dispatchedAt} from now on
 * @param trucks the trucks of the variant's routes, now unavailable (on route), in route order
 * @param removedShipments the shipments carried on those routes, now deleted, in route and stop order
 */
public record DispatchResponse(
        UUID planId,
        Mode mode,
        LocalDateTime dispatchedAt,
        List<DispatchedTruck> trucks,
        List<RemovedShipment> removedShipments) {

    public DispatchResponse {
        trucks = List.copyOf(trucks);
        removedShipments = List.copyOf(removedShipments);
    }

    /** A truck marked as on route. */
    public record DispatchedTruck(long id, String plate) {
    }

    /** A shipment removed from the shipment list because it is on its way. */
    public record RemovedShipment(long id, String reference) {
    }
}
