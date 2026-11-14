package com.haulwise.dto.plan;

import com.haulwise.domain.Priority;
import com.haulwise.solver.model.DistanceSource;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One delivery stop of a route, with the leg that leads to it.
 *
 * @param sequence 1-based position on the route
 * @param shipmentIds shipments unloaded here, most important first
 * @param shipmentReferences their references, in the same order as {@code shipmentIds}; taken when the plan is
 *        created, so a stored plan keeps them even if a shipment is later edited or deleted
 * @param priority most important priority of the shipments unloaded here
 * @param pinned whether this stop carries the truck's kept pin-first request (after PIN_CONFLICT resolution). The
 *        same in both variants: only PRIORITY forces the stop first, OPTIMAL ignores the pin, so in OPTIMAL a
 *        pinned stop can be at any position
 * @param legCostPln fuel and driver cost of the leg, incl. the service time at this stop
 * @param loadWeightKgOnLeg weight on board while driving to this stop
 * @param arrivalTime departure from the depot + the leg's arrival offset, whole minutes
 * @param departureTime arrival + service time
 * @param deadline earliest deadline of the shipments unloaded here, or {@code null}
 * @param deadlineMissed whether the truck arrives after {@code deadline}
 */
public record StopResponse(
        int sequence,
        long warehouseId,
        String name,
        String city,
        double lat,
        double lon,
        List<Long> shipmentIds,
        List<String> shipmentReferences,
        int dropPallets,
        int dropWeightKg,
        Priority priority,
        @Schema(description = "The truck's kept pin-first request; only PRIORITY forces this stop first, "
                + "OPTIMAL ignores it")
        boolean pinned,
        BigDecimal legDistanceKm,
        long legDurationMinutes,
        BigDecimal legLitres,
        BigDecimal legCostPln,
        DistanceSource legSource,
        long loadWeightKgOnLeg,
        LocalDateTime arrivalTime,
        LocalDateTime departureTime,
        LocalDateTime deadline,
        boolean deadlineMissed) {

    public StopResponse {
        shipmentIds = List.copyOf(shipmentIds);
        shipmentReferences = List.copyOf(shipmentReferences);
        if (shipmentReferences.size() != shipmentIds.size()) {
            throw new IllegalArgumentException("One reference per shipment id expected");
        }
    }
}
