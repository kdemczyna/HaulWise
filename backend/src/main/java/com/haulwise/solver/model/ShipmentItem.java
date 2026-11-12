package com.haulwise.solver.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * A shipment as seen by the solver. Atomic: it is loaded onto exactly one truck or left unassigned.
 *
 * @param id shipment id
 * @param destinationId warehouse the shipment is delivered to
 * @param pallets pallet count, &gt;= 1
 * @param weightKg weight, &gt;= 1
 * @param priority delivery priority, not null
 * @param pinnedFirst whether the dispatcher wants this shipment's stop first on its route
 * @param deadline latest arrival (Europe/Warsaw local time, D-10), or {@code null} for none
 */
public record ShipmentItem(
        long id,
        long destinationId,
        int pallets,
        int weightKg,
        Priority priority,
        boolean pinnedFirst,
        LocalDateTime deadline) {

    public ShipmentItem {
        if (pallets < 1) {
            throw new IllegalArgumentException("pallets must be >= 1, was " + pallets);
        }
        if (weightKg < 1) {
            throw new IllegalArgumentException("weightKg must be >= 1, was " + weightKg);
        }
        Objects.requireNonNull(priority, "priority");
    }
}
