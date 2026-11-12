package com.haulwise.dto;

import com.haulwise.domain.Settings;

public record SettingsResponse(
        double dieselPricePlnPerLitre,
        double averageSpeedKmh,
        double roadFactor,
        int serviceMinutesPerStop,
        double urgentWeightPlnPerHour,
        double highWeightPlnPerHour,
        boolean complianceEnabled,
        int maxContinuousDrivingMinutes,
        int breakMinutes,
        int maxDailyDrivingMinutes,
        int consolidationFillThresholdPct) {

    public static SettingsResponse from(Settings settings) {
        return new SettingsResponse(settings.getDieselPricePlnPerLitre(), settings.getAverageSpeedKmh(),
                settings.getRoadFactor(), settings.getServiceMinutesPerStop(), settings.getUrgentWeightPlnPerHour(),
                settings.getHighWeightPlnPerHour(), settings.isComplianceEnabled(),
                settings.getMaxContinuousDrivingMinutes(), settings.getBreakMinutes(),
                settings.getMaxDailyDrivingMinutes(), settings.getConsolidationFillThresholdPct());
    }
}
