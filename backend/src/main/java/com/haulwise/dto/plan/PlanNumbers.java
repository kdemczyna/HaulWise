package com.haulwise.dto.plan;

import com.haulwise.solver.Rounding;
import java.math.BigDecimal;

/**
 * Display rounding of plan numbers (ARCHITECTURE section 5): km, litres and percentages to 1 decimal, PLN to 2,
 * minutes and kilograms to whole numbers. Always {@link Rounding#halfUp}, so the numbers match the delta sentence.
 */
public final class PlanNumbers {

    private PlanNumbers() {
    }

    public static BigDecimal km(double value) {
        return Rounding.halfUp(value, 1);
    }

    public static BigDecimal litres(double value) {
        return Rounding.halfUp(value, 1);
    }

    public static BigDecimal pln(double value) {
        return Rounding.halfUp(value, 2);
    }

    public static BigDecimal percent(double value) {
        return Rounding.halfUp(value, 1);
    }

    /** Whole minutes; also used for clock times (departure + rounded offset). */
    public static long minutes(double value) {
        return Rounding.halfUp(value, 0).longValueExact();
    }

    public static long kg(double value) {
        return Rounding.halfUp(value, 0).longValueExact();
    }
}
