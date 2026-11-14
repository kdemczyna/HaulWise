package com.haulwise.solver.model;

/**
 * Kinds of solver warnings (ARCHITECTURE section 5). DEADLINE_MISSED and ESTIMATED_DISTANCE are derived by the
 * plan service, not the solver.
 */
public enum WarningCode {
    /** More than one destination on a truck was pinned first; only one pin was kept. */
    PIN_CONFLICT,
    /** Continuous driving reaches the limit on a leg; a break is due (T-22). */
    BREAK_REQUIRED,
    /** A route's total driving exceeds the daily limit (T-22). */
    DAILY_DRIVING_EXCEEDED
}
