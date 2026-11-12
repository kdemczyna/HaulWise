package com.haulwise.solver.model;

import java.util.List;
import java.util.Objects;

/**
 * Something the dispatcher should know about a plan; it does not stop the plan from being made.
 *
 * @param code what kind of warning
 * @param message plain-language explanation, not blank
 * @param truckId truck the warning is about
 * @param shipmentIds shipments the warning is about (for PIN_CONFLICT: those whose pin was dropped);
 *        copied, unmodifiable
 */
public record Warning(WarningCode code, String message, long truckId, List<Long> shipmentIds) {

    public Warning {
        Objects.requireNonNull(code, "code");
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("message must not be blank");
        }
        shipmentIds = List.copyOf(shipmentIds);
    }
}
