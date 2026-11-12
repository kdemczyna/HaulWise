package com.haulwise.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code PUT /api/settings}: the full parameter set (no partial update). Ranges keep the values
 * physically meaningful (ARCHITECTURE section 6.4); cross-field rules are checked by the service.
 */
public record SettingsRequest(
        @NotNull @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("50.0") Double dieselPricePlnPerLitre,
        @NotNull @DecimalMin("10.0") @DecimalMax("90.0") Double averageSpeedKmh,
        @NotNull @DecimalMin("1.0") @DecimalMax("3.0") Double roadFactor,
        @NotNull @Min(0) @Max(480) Integer serviceMinutesPerStop,
        @NotNull @DecimalMin("0.0") @DecimalMax("10000.0") Double urgentWeightPlnPerHour,
        @NotNull @DecimalMin("0.0") @DecimalMax("10000.0") Double highWeightPlnPerHour,
        @NotNull Boolean complianceEnabled,
        @NotNull @Min(30) @Max(900) Integer maxContinuousDrivingMinutes,
        @NotNull @Min(15) @Max(180) Integer breakMinutes,
        @NotNull @Min(60) @Max(900) Integer maxDailyDrivingMinutes,
        @NotNull @Min(1) @Max(100) Integer consolidationFillThresholdPct) {
}
