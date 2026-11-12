package com.haulwise.solver.model;

/**
 * Argument checks shared by the solver model records.
 */
final class Checks {

    private Checks() {
    }

    static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(name + " must be a finite number >= 0, was " + value);
        }
    }
}
