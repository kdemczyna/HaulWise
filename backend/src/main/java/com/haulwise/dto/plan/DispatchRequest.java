package com.haulwise.dto.plan;

import com.haulwise.solver.model.Mode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code POST /api/plans/{id}/dispatch} (T-33).
 *
 * @param mode the variant to send - the one the dispatcher is looking at
 */
public record DispatchRequest(
        @Schema(description = "The plan variant to send (OPTIMAL or PRIORITY)")
        @NotNull Mode mode) {
}
