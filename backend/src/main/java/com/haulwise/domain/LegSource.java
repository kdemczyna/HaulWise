package com.haulwise.domain;

/**
 * Origin of a stored route leg. Haversine estimates are never stored (D-06).
 */
public enum LegSource {
    SEEDED,
    OSRM
}
