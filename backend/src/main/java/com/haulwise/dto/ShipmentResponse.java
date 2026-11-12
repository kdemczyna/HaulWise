package com.haulwise.dto;

import com.haulwise.domain.Priority;
import com.haulwise.domain.Shipment;
import java.time.LocalDateTime;

/**
 * Map only shipments whose origin and destination are initialised (inside a transaction, or loaded
 * with {@code findWithWarehousesById} / {@code search}); open-in-view is off.
 */
public record ShipmentResponse(
        Long id,
        String reference,
        WarehouseSummary origin,
        WarehouseSummary destination,
        int pallets,
        int weightKg,
        Priority priority,
        LocalDateTime deadline,
        boolean pinnedFirst) {

    public static ShipmentResponse from(Shipment shipment) {
        return new ShipmentResponse(shipment.getId(), shipment.getReference(),
                WarehouseSummary.from(shipment.getOrigin()), WarehouseSummary.from(shipment.getDestination()),
                shipment.getPallets(), shipment.getWeightKg(), shipment.getPriority(), shipment.getDeadline(),
                shipment.isPinnedFirst());
    }
}
