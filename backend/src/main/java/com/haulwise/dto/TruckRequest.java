package com.haulwise.dto;

import com.haulwise.domain.Truck;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST/PUT /api/trucks}. The plate is normalised on construction
 * ({@link Truck#normalisePlate(String)}), so validation, the uniqueness check and storage agree.
 */
public record TruckRequest(
        @NotNull @Size(min = 2, max = 15) String plate,
        @NotNull @Min(1) @Max(40) Integer capacityPallets,
        @NotNull @Min(1) @Max(40000) Integer payloadKg,
        @NotNull @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("100.0")
        Double baseConsumptionLPer100Km,
        @NotNull @DecimalMin("0.0") @DecimalMax("50.0") Double extraConsumptionFullLoadLPer100Km,
        @NotNull @DecimalMin("0.0") @DecimalMax("1000.0") Double driverCostPlnPerHour) {

    public TruckRequest {
        plate = Truck.normalisePlate(plate);
    }
}
