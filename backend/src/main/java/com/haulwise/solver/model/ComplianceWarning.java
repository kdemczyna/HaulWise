package com.haulwise.solver.model;

import java.util.Objects;

/**
 * A driving-time finding of the {@code ComplianceChecker} for one truck's route (T-22). It only annotates the
 * route: the schedule, cost and order stay as planned (D-09). Ids and minute offsets only; the plan service
 * builds the user-facing text with plates, warehouse names and clock times.
 */
public sealed interface ComplianceWarning {

    long truckId();

    WarningCode code();

    /**
     * The continuous-driving limit is reached while driving {@code leg}.
     *
     * @param leg the leg being driven when the limit is reached
     * @param atMinutes minutes after departure (on the planned schedule, which has no breaks) at which the limit
     *        is reached: the latest moment to start the break
     * @param continuousLimitMinutes the limit that was reached
     * @param breakMinutes the break to take
     */
    record BreakRequired(long truckId, DirectedPair leg, double atMinutes, int continuousLimitMinutes,
                         int breakMinutes) implements ComplianceWarning {

        public BreakRequired {
            Objects.requireNonNull(leg, "leg");
            Checks.requireNonNegative(atMinutes, "atMinutes");
        }

        @Override
        public WarningCode code() {
            return WarningCode.BREAK_REQUIRED;
        }
    }

    /**
     * The whole route drives longer than the daily limit allows.
     *
     * @param drivingMinutes total driving of the route, return leg included
     * @param dailyLimitMinutes the daily limit
     */
    record DailyDrivingExceeded(long truckId, double drivingMinutes, int dailyLimitMinutes)
            implements ComplianceWarning {

        public DailyDrivingExceeded {
            Checks.requireNonNegative(drivingMinutes, "drivingMinutes");
        }

        @Override
        public WarningCode code() {
            return WarningCode.DAILY_DRIVING_EXCEEDED;
        }
    }
}
