package com.haulwise.service;

import com.haulwise.dto.plan.PlanWarningCode;
import com.haulwise.dto.plan.WarningResponse;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * User-facing texts of plan warnings. The solver reports warnings with ids only; the texts are built here from
 * plates, cities and shipment references.
 */
final class PlanWarnings {

    static final int MAX_LISTED_LEGS = 5;

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private PlanWarnings() {
    }

    /**
     * "Truck WX 1001A has more than one pinned stop: Gdansk stays first; the pin of PO-1050 was dropped, so it is
     * ordered by priority."
     */
    static WarningResponse pinConflict(long truckId, String plate, String keptCity, List<String> droppedReferences,
                                       List<Long> droppedShipmentIds) {
        String dropped = droppedReferences.size() == 1
                ? "the pin of " + droppedReferences.get(0) + " was dropped, so it is"
                : "the pins of " + joinWithAnd(droppedReferences) + " were dropped, so they are";
        String message = "Truck " + plate + " has more than one pinned stop: " + keptCity + " stays first; "
                + dropped + " ordered by priority.";
        return WarningResponse.of(PlanWarningCode.PIN_CONFLICT, message, truckId, droppedShipmentIds);
    }

    /**
     * "WX 1001A arrives in Gdansk at 2026-11-13 23:18, after the deadline of PO-1042 (2026-11-13 12:00)."
     */
    static WarningResponse deadlineMissed(long truckId, String plate, String city, LocalDateTime arrival,
                                          List<LateShipment> late) {
        List<String> deadlines = late.stream()
                .map(shipment -> shipment.reference() + " (" + TIME.format(shipment.deadline()) + ")")
                .toList();
        String message = plate + " arrives in " + city + " at " + TIME.format(arrival) + ", after the deadline"
                + (late.size() == 1 ? "" : "s") + " of " + joinWithAnd(deadlines) + ".";
        return WarningResponse.of(PlanWarningCode.DEADLINE_MISSED, message, truckId,
                late.stream().map(LateShipment::shipmentId).toList());
    }

    /**
     * "1 leg uses an estimated distance (straight line x road factor), so its time and cost are approximate:
     * WAW Central -> New Hub."
     */
    static WarningResponse estimatedDistance(List<String> legNames) {
        int count = legNames.size();
        String listed = legNames.size() <= MAX_LISTED_LEGS
                ? String.join(", ", legNames)
                : String.join(", ", legNames.subList(0, MAX_LISTED_LEGS)) + " and "
                        + (count - MAX_LISTED_LEGS) + " more";
        String message = count == 1
                ? "1 leg uses an estimated distance (straight line x road factor), so its time and cost are "
                        + "approximate: " + listed + "."
                : count + " legs use estimated distances (straight line x road factor), so their times and costs "
                        + "are approximate: " + listed + ".";
        return WarningResponse.of(PlanWarningCode.ESTIMATED_DISTANCE, message, null, List.of());
    }

    /** "A", "A and B", "A, B and C". */
    static String joinWithAnd(List<String> items) {
        if (items.size() <= 1) {
            return String.join("", items);
        }
        return String.join(", ", items.subList(0, items.size() - 1)) + " and " + items.get(items.size() - 1);
    }

    /** A shipment reached after its own deadline. */
    record LateShipment(long shipmentId, String reference, LocalDateTime deadline) {
    }
}
