package com.haulwise.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.haulwise.domain.Settings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

@DataJpaTest
class SettingsRepositoryTest {

    @Autowired
    private SettingsRepository settingsRepository;

    @Autowired
    private TestEntityManager em;

    @Test
    void persistsDefaultsUnderTheSingletonId() {
        settingsRepository.saveAndFlush(Settings.withDefaults());
        em.clear();

        Settings loaded = settingsRepository.findById(Settings.SINGLETON_ID).orElseThrow();

        assertThat(loaded.getDieselPricePlnPerLitre()).isEqualTo(6.20);
        assertThat(loaded.getAverageSpeedKmh()).isEqualTo(70);
        assertThat(loaded.getRoadFactor()).isEqualTo(1.3);
        assertThat(loaded.getServiceMinutesPerStop()).isEqualTo(30);
        assertThat(loaded.getUrgentWeightPlnPerHour()).isEqualTo(150);
        assertThat(loaded.getHighWeightPlnPerHour()).isEqualTo(50);
        assertThat(loaded.isComplianceEnabled()).isTrue();
        assertThat(loaded.getMaxContinuousDrivingMinutes()).isEqualTo(270);
        assertThat(loaded.getBreakMinutes()).isEqualTo(45);
        assertThat(loaded.getMaxDailyDrivingMinutes()).isEqualTo(540);
        assertThat(loaded.getConsolidationFillThresholdPct()).isEqualTo(60);
    }

    @Test
    void savingAgainUpdatesTheSingleRowInsteadOfAddingOne() {
        settingsRepository.saveAndFlush(Settings.withDefaults());

        Settings changed = Settings.withDefaults();
        changed.setDieselPricePlnPerLitre(6.85);
        changed.setComplianceEnabled(false);
        settingsRepository.saveAndFlush(changed);
        em.clear();

        assertThat(settingsRepository.count()).isEqualTo(1);
        Settings loaded = settingsRepository.findById(Settings.SINGLETON_ID).orElseThrow();
        assertThat(loaded.getDieselPricePlnPerLitre()).isEqualTo(6.85);
        assertThat(loaded.isComplianceEnabled()).isFalse();
    }
}
