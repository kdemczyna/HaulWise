package com.haulwise.repository;

import com.haulwise.domain.LegSource;

/**
 * Read-only projection of a {@link com.haulwise.domain.RouteLeg} with plain warehouse ids
 * (no lazy proxies to initialise).
 */
public record StoredLeg(Long fromId, Long toId, double distanceKm, double durationMinutes, LegSource source) {
}
