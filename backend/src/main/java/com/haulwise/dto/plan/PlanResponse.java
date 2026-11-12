package com.haulwise.dto.plan;

import com.haulwise.dto.WarehouseResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * A computed plan (ARCHITECTURE section 5): both variants over the same truck assignment, the cost of priority,
 * unassigned shipments and plan-wide warnings. Immutable snapshot kept in the in-memory plan store (D-05).
 *
 * @param request the request as planned, defaults resolved
 * @param warnings plan-wide warnings (PIN_CONFLICT, ESTIMATED_DISTANCE)
 */
public record PlanResponse(
        UUID id,
        LocalDateTime createdAt,
        PlanRequest request,
        WarehouseResponse depot,
        PlanVariantResponse optimal,
        PlanVariantResponse priority,
        DeltaResponse delta,
        List<UnassignedResponse> unassigned,
        List<WarningResponse> warnings) {

    public PlanResponse {
        unassigned = List.copyOf(unassigned);
        warnings = List.copyOf(warnings);
    }
}
