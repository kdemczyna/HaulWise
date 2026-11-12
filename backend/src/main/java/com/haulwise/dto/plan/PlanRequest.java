package com.haulwise.dto.plan;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Body of {@code POST /api/plans}. Validated by {@code PlanService} together with the existence checks, so all
 * problems come back in one 400. Echoed in the plan response with the defaults filled in.
 *
 * @param depotId warehouse every selected shipment starts at
 * @param shipmentIds shipments to plan, distinct, all from the depot
 * @param truckIds trucks to use, distinct
 * @param returnToDepot whether every truck drives back to the depot; {@code null} = {@code true}
 * @param departureTime local Europe/Warsaw departure from the depot; {@code null} = tomorrow 06:00
 */
public record PlanRequest(
        @NotNull Long depotId,
        @NotNull @Size(min = 1, max = PlanRequest.MAX_IDS) List<Long> shipmentIds,
        @NotNull @Size(min = 1, max = PlanRequest.MAX_IDS) List<Long> truckIds,
        @Schema(description = "Whether every truck drives back to the depot; default true")
        Boolean returnToDepot,
        @Schema(description = "Local date-time without offset; default tomorrow 06:00",
                example = "2026-11-13T06:00:00")
        LocalDateTime departureTime) {

    /** Largest number of shipment or truck ids in one request (ARCHITECTURE 7). */
    public static final int MAX_IDS = 200;

    /** The request as it was planned: defaults resolved, id lists copied (call only after validation: no nulls). */
    public PlanRequest resolved(boolean resolvedReturnToDepot, LocalDateTime resolvedDepartureTime) {
        return new PlanRequest(depotId, List.copyOf(shipmentIds), List.copyOf(truckIds), resolvedReturnToDepot,
                resolvedDepartureTime);
    }
}
