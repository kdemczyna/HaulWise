package com.haulwise.dto;

import com.haulwise.domain.Priority;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;

/**
 * Body of {@code POST/PUT /api/shipments}. Pallets and weight may exceed every truck (D-15) so the
 * planner can report them as unassigned. {@code priority} defaults to NORMAL, {@code pinnedFirst} to
 * false. The reference is stripped on construction. Existence of the warehouses and {@code originId != destinationId} are checked by the service.
 */
public record ShipmentRequest(
        @NotNull @Size(min = 1, max = 40) String reference,
        @NotNull Long originId,
        @NotNull Long destinationId,
        @NotNull @Min(1) @Max(100) Integer pallets,
        @NotNull @Min(1) @Max(100000) Integer weightKg,
        Priority priority,
        LocalDateTime deadline,
        Boolean pinnedFirst) {

    public ShipmentRequest {
        reference = reference == null ? null : reference.strip();
    }
}
