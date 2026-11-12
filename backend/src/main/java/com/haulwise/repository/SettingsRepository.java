package com.haulwise.repository;

import com.haulwise.domain.Settings;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Holds the single {@link Settings} row with id {@link Settings#SINGLETON_ID}.
 */
public interface SettingsRepository extends JpaRepository<Settings, Long> {
}
