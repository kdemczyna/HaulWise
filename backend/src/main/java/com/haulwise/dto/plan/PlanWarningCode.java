package com.haulwise.dto.plan;

/**
 * Plan warning codes (ARCHITECTURE section 5), each with its severity. PIN_CONFLICT comes from the solver;
 * DEADLINE_MISSED and ESTIMATED_DISTANCE are derived by the plan service (the solver works with minute offsets,
 * not clock times). BREAK_REQUIRED and DAILY_DRIVING_EXCEEDED are added by T-22.
 */
public enum PlanWarningCode {
    /** More than one stop on a truck was pinned first; only one pin was kept. */
    PIN_CONFLICT(WarningSeverity.WARNING),
    /** A stop is reached after the deadline of one of its shipments. */
    DEADLINE_MISSED(WarningSeverity.WARNING),
    /** A driven leg uses a straight-line estimate instead of a stored road distance. */
    ESTIMATED_DISTANCE(WarningSeverity.INFO);

    private final WarningSeverity severity;

    PlanWarningCode(WarningSeverity severity) {
        this.severity = severity;
    }

    public WarningSeverity severity() {
        return severity;
    }
}
