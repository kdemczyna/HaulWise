package com.haulwise.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.haulwise.HaulWiseApiTest;
import com.haulwise.domain.Settings;
import com.haulwise.repository.SettingsRepository;
import com.haulwise.solver.model.CostParameters;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@HaulWiseApiTest
class SettingsServiceTest {

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private SettingsRepository settingsRepository;

    @BeforeEach
    void removeSettingsRow() {
        settingsRepository.deleteAll();
    }

    @Test
    void estimateParametersDefaultAndCreateTheRow() {
        assertThat(settingsService.estimateParameters()).isEqualTo(new EstimateParameters(1.3, 70));
        assertThat(settingsRepository.findById(Settings.SINGLETON_ID)).isPresent();
    }

    @Test
    void estimateParametersReflectStoredSettings() {
        Settings settings = Settings.withDefaults();
        settings.setRoadFactor(1.45);
        settings.setAverageSpeedKmh(62);
        settingsRepository.save(settings);

        assertThat(settingsService.estimateParameters()).isEqualTo(new EstimateParameters(1.45, 62));
    }

    @Test
    void costParametersDefaultToPolishSettings() {
        assertThat(settingsService.costParameters()).isEqualTo(new CostParameters(6.20, 30));
    }

    @Test
    void costParametersReflectStoredSettings() {
        Settings settings = Settings.withDefaults();
        settings.setDieselPricePlnPerLitre(5.89);
        settings.setServiceMinutesPerStop(20);
        settingsRepository.save(settings);

        assertThat(settingsService.costParameters()).isEqualTo(new CostParameters(5.89, 20));
    }
}
