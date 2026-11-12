package com.haulwise.dto.plan;

import java.util.List;

/**
 * A plan warning; the severity follows from the code.
 *
 * @param truckId truck the warning is about, or {@code null} for plan-wide warnings
 * @param shipmentIds shipments the warning is about (PIN_CONFLICT: the dropped pins; DEADLINE_MISSED: the late
 *        shipments), possibly empty
 */
public record WarningResponse(
        PlanWarningCode code, WarningSeverity severity, String message, Long truckId, List<Long> shipmentIds) {

    public WarningResponse {
        shipmentIds = List.copyOf(shipmentIds);
    }

    public static WarningResponse of(PlanWarningCode code, String message, Long truckId, List<Long> shipmentIds) {
        return new WarningResponse(code, code.severity(), message, truckId, shipmentIds);
    }
}
