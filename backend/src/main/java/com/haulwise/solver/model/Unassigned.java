package com.haulwise.solver.model;

import java.util.Objects;

/**
 * A shipment the solver could not load onto any truck.
 *
 * @param shipmentId the shipment
 * @param reason machine-readable reason
 * @param message human-readable explanation with the numbers involved
 */
public record Unassigned(long shipmentId, UnassignedReason reason, String message) {

    public Unassigned {
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(message, "message");
    }
}
