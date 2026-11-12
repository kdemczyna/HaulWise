package com.haulwise.solver.model;

/**
 * The two plan variants (ARCHITECTURE 6.3): {@code OPTIMAL} orders stops by cost only, {@code PRIORITY} adds
 * the priority penalty and keeps the pinned stop first.
 */
public enum Mode {
    OPTIMAL,
    PRIORITY
}
