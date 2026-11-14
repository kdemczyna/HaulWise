package com.haulwise.dto.plan;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One entry of {@code GET /api/plans} (T-34): enough to recognise a stored plan and open it, without its routes.
 *
 * @param depotName the depot's name when the plan was made (a snapshot, like the rest of the plan)
 * @param dispatchedAt when the plan was sent (T-33); {@code null} while it is a draft
 * @param shipmentCount number of shipments selected for the run (assigned or not)
 * @param truckCount number of trucks selected for the run (used or not)
 */
public record PlanSummaryResponse(
        UUID id,
        Long depotId,
        String depotName,
        LocalDateTime createdAt,
        LocalDateTime dispatchedAt,
        int shipmentCount,
        int truckCount) {

    public static PlanSummaryResponse from(PlanResponse plan) {
        return new PlanSummaryResponse(plan.id(), plan.depot().id(), plan.depot().name(), plan.createdAt(),
                plan.dispatchedAt(), plan.request().shipmentIds().size(), plan.request().truckIds().size());
    }
}
