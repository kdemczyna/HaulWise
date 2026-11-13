package com.haulwise.dto;

import com.haulwise.domain.Truck;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST/PUT /api/trucks}. The plate is normalised on construction
 * ({@link Truck#normalisePlate(String)}), so validation, the uniqueness check and storage agree.
 *
 * @param homeWarehouseId the depot the truck is based at (T-32); must exist
 * @param available whether the truck can be planned (T-33); {@code null} = {@code true} on create and
 *        "unchanged" on update, so an edit form that does not send it never clears an on-route status
 */
public record TruckRequest(
        @NotNull @Size(min = 2, max = 15) String plate,
        @NotNull @Min(1) @Max(40) Integer capacityPallets,
        @NotNull @Min(1) @Max(40000) Integer payloadKg,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("100.0")
        Double baseConsumptionLPer100Km,
        @NotNull @DecimalMin("0.0") @DecimalMax("50.0") Double extraConsumptionFullLoadLPer100Km,
        @NotNull @DecimalMin("0.0") @DecimalMax("1000.0") Double driverCostPlnPerHour,
        @NotNull Long homeWarehouseId,
        @Schema(description = "Whether the truck can be planned (false = on route); default true on create, "
                + "unchanged on update")
        Boolean available) {

    public TruckRequest {
        plate = Truck.normalisePlate(plate);
    }
}
