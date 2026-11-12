package com.haulwise.solver;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * The one rounding rule for numbers shown to people (ARCHITECTURE 5, 6.5): half-up on the absolute value
 * ({@code 2.5 -> 3}, {@code -2.5 -> -3}) of the exact decimal form of the double. Used by the delta sentence
 * and by the plan DTO mapping (T-13), so the sentence and the displayed numbers always agree.
 */
public final class Rounding {

    private Rounding() {
    }

    /** {@code value} rounded half-up to {@code scale} decimals (0 = whole numbers). */
    public static BigDecimal halfUp(double value, int scale) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Cannot round " + value);
        }
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP);
    }
}
