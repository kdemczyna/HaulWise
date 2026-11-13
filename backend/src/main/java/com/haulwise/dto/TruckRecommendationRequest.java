package com.haulwise.dto;

import com.haulwise.dto.plan.PlanRequest;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body of {@code POST /api/trucks/recommend}. Validated by {@code TruckRecommendationService} together with
 * the existence checks (same pattern as {@link PlanRequest}), so all problems come back in one 400.
 *
 * @param depotId warehouse every selected shipment starts at
 * @param shipmentIds shipments to carry, distinct, all from the depot
 * @param returnToDepot whether every recommended truck drives back to the depot; {@code null} = {@code true}
 */
public record TruckRecommendationRequest(
        @NotNull Long depotId,
        @NotNull @Size(min = 1, max = PlanRequest.MAX_IDS) List<Long> shipmentIds,
        @Schema(description = "Whether every recommended truck drives back to the depot; default true")
        Boolean returnToDepot) {
}
