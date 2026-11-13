package com.haulwise.dto;

import com.haulwise.dto.plan.TotalsResponse;
import com.haulwise.dto.plan.UnassignedResponse;
import java.math.BigDecimal;
import java.util.List;

/**
 * Response of {@code POST /api/trucks/recommend} (ARCHITECTURE section 5, T-30): the cheapest combination of
 * the full fleet that carries every requested shipment, or - when none does, not even the whole fleet - the
 * shipments that do not fit anywhere and why.
 *
 * @param truckIds recommended truck ids, ascending; empty when infeasible
 * @param trucks the same trucks, with their plate; empty when infeasible
 * @param totals combined distance, time, fuel and cost of the recommended combination; {@code null} when infeasible
 * @param utilisationPct average pallet utilisation across the recommended trucks (ARCHITECTURE section 5);
 *        {@code null} when infeasible
 * @param unassigned shipments that fit no combination of the fleet, with the reason; empty when feasible
 */
public record TruckRecommendationResponse(
        List<Long> truckIds,
        List<RecommendedTruckResponse> trucks,
        TotalsResponse totals,
        BigDecimal utilisationPct,
        List<UnassignedResponse> unassigned) {

    /** No combination of the fleet - not even all of it - can carry every requested shipment. */
    public static TruckRecommendationResponse infeasible(List<UnassignedResponse> unassigned) {
        return new TruckRecommendationResponse(List.of(), List.of(), null, null, unassigned);
    }
}
