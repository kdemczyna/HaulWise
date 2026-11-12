package com.haulwise.dto.plan;

import com.haulwise.domain.Priority;
import com.haulwise.solver.model.DistanceSource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * One delivery stop of a route, with the leg that leads to it.
 *
 * @param sequence 1-based position on the route
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
        int dropPallets,
        int dropWeightKg,
        Priority priority,
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
    }
}
