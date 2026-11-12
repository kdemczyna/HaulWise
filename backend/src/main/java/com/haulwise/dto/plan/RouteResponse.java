package com.haulwise.dto.plan;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One truck's route in a plan variant.
 *
 * @param colorIndex position of the truck in the plan (same truck, same index in both variants), for the map
 * @param returnTime arrival back at the depot, or {@code null} when the plan does not return to the depot
 * @param returnLeg the leg back to the depot, or {@code null} when the plan does not return to the depot
 * @param warnings warnings about this truck in this variant (DEADLINE_MISSED)
 */
public record RouteResponse(
        long truckId,
        String plate,
        int colorIndex,
        UtilisationResponse utilisation,
        LocalDateTime departureTime,
        LocalDateTime returnTime,
        List<StopResponse> stops,
        ReturnLegResponse returnLeg,
        TotalsResponse totals,
        List<WarningResponse> warnings) {

    public RouteResponse {
        stops = List.copyOf(stops);
        warnings = List.copyOf(warnings);
    }
}
