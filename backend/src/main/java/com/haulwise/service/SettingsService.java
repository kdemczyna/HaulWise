package com.haulwise.service;

import com.haulwise.domain.Settings;
import com.haulwise.dto.SettingsRequest;
import com.haulwise.dto.SettingsResponse;
import com.haulwise.exception.FieldViolation;
import com.haulwise.exception.RequestValidationException;
import com.haulwise.repository.SettingsRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads and replaces the single settings row. The row is created with the section 6.4 defaults on first
 * access, so the API works with seeding disabled. Reads are therefore not read-only transactions.
 */
@Service
@Transactional
public class SettingsService {

    private final SettingsRepository settingsRepository;

    public SettingsService(SettingsRepository settingsRepository) {
        this.settingsRepository = settingsRepository;
    }

    public SettingsResponse get() {
        return SettingsResponse.from(loadOrCreate());
    }

    /** Road factor and average speed for distance estimates; for other services, not the API. */
    public EstimateParameters estimateParameters() {
        Settings settings = loadOrCreate();
        return new EstimateParameters(settings.getRoadFactor(), settings.getAverageSpeedKmh());
    }

    public SettingsResponse update(SettingsRequest request) {
        checkCrossFieldRules(request);
        Settings settings = loadOrCreate();
        settings.setDieselPricePlnPerLitre(request.dieselPricePlnPerLitre());
        settings.setAverageSpeedKmh(request.averageSpeedKmh());
        settings.setRoadFactor(request.roadFactor());
        settings.setServiceMinutesPerStop(request.serviceMinutesPerStop());
        settings.setUrgentWeightPlnPerHour(request.urgentWeightPlnPerHour());
        settings.setHighWeightPlnPerHour(request.highWeightPlnPerHour());
        settings.setComplianceEnabled(request.complianceEnabled());
        settings.setMaxContinuousDrivingMinutes(request.maxContinuousDrivingMinutes());
        settings.setBreakMinutes(request.breakMinutes());
        settings.setMaxDailyDrivingMinutes(request.maxDailyDrivingMinutes());
        settings.setConsolidationFillThresholdPct(request.consolidationFillThresholdPct());
        return SettingsResponse.from(settings);
    }

    private Settings loadOrCreate() {
        return settingsRepository.findById(Settings.SINGLETON_ID)
                .orElseGet(() -> settingsRepository.save(Settings.withDefaults()));
    }

    /** URGENT must weigh at least as much as HIGH; the continuous-driving limit cannot exceed the daily limit. */
    private static void checkCrossFieldRules(SettingsRequest request) {
        List<FieldViolation> violations = new ArrayList<>();
        if (request.urgentWeightPlnPerHour() < request.highWeightPlnPerHour()) {
            violations.add(new FieldViolation("urgentWeightPlnPerHour",
                    "must be greater than or equal to highWeightPlnPerHour"));
        }
        if (request.maxContinuousDrivingMinutes() > request.maxDailyDrivingMinutes()) {
            violations.add(new FieldViolation("maxContinuousDrivingMinutes",
                    "must be less than or equal to maxDailyDrivingMinutes"));
        }
        if (!violations.isEmpty()) {
            throw new RequestValidationException(violations);
        }
    }
}
