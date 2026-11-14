package com.haulwise.solver.model;

/**
 * EU driving-time limits checked by the {@code ComplianceChecker} (Regulation (EC) 561/2006, Art. 6 and 7;
 * ARCHITECTURE 6.4). Present in the solver input only when compliance checking is enabled in the settings.
 *
 * @param maxContinuousDrivingMinutes driving allowed before a break is required (default 270 = 4.5 h), &gt;= 1
 * @param breakMinutes length of the suggested break (default 45), &gt;= 1
 * @param maxDailyDrivingMinutes driving allowed in one route (default 540 = 9 h), &gt;= 1
 */
public record ComplianceRules(int maxContinuousDrivingMinutes, int breakMinutes, int maxDailyDrivingMinutes) {

    public ComplianceRules {
        requirePositive(maxContinuousDrivingMinutes, "maxContinuousDrivingMinutes");
        requirePositive(breakMinutes, "breakMinutes");
        requirePositive(maxDailyDrivingMinutes, "maxDailyDrivingMinutes");
    }

    private static void requirePositive(int value, String name) {
        if (value < 1) {
            throw new IllegalArgumentException(name + " must be >= 1, was " + value);
        }
    }
}
