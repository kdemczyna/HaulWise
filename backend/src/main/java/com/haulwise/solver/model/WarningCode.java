package com.haulwise.solver.model;

/**
 * Kinds of solver warnings (ARCHITECTURE section 5). Further codes (DEADLINE_MISSED, ESTIMATED_DISTANCE,
 * BREAK_REQUIRED, DAILY_DRIVING_EXCEEDED) are added by the tasks that detect them.
 */
public enum WarningCode {
    /** More than one destination on a truck was pinned first; only one pin was kept. */
    PIN_CONFLICT
}
