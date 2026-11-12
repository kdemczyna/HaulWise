package com.haulwise.solver.model;

/**
 * How much an hour of delay is worth per priority in the PRIORITY objective (ARCHITECTURE 6.3, D-13). The
 * weights only steer the stop order; they never appear in a reported cost. NORMAL always weighs 0.
 *
 * @param urgentPlnPerHour penalty per hour until arrival at a stop with an URGENT shipment, &gt;= 0
 * @param highPlnPerHour penalty per hour until arrival at a stop whose most important shipment is HIGH, &gt;= 0
 */
public record PriorityWeights(double urgentPlnPerHour, double highPlnPerHour) {

    public PriorityWeights {
        Checks.requireNonNegative(urgentPlnPerHour, "urgentPlnPerHour");
        Checks.requireNonNegative(highPlnPerHour, "highPlnPerHour");
    }

    /** Penalty in PLN per hour of arrival time for a stop of the given priority. */
    public double plnPerHour(Priority priority) {
        return switch (priority) {
            case URGENT -> urgentPlnPerHour;
            case HIGH -> highPlnPerHour;
            case NORMAL -> 0;
        };
    }
}
