package com.haulwise.solver.model;

/**
 * Where a leg's distance and duration come from. {@code ESTIMATED} legs are computed on the fly
 * (great-circle distance x road factor) and are never stored (D-06).
 */
public enum DistanceSource {
    SEEDED,
    OSRM,
    ESTIMATED
}
