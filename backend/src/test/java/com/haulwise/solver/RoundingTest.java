package com.haulwise.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RoundingTest {

    @ParameterizedTest(name = "{0} to {1} decimals -> {2}")
    @CsvSource({"2.5, 0, 3", "-2.5, 0, -3", "2.49, 0, 2", "112.345, 2, 112.35", "112.344, 2, 112.34",
            "38.05, 1, 38.1", "-0.004, 2, 0.00", "7, 2, 7.00"})
    void roundsHalfUpAwayFromZero(double value, int scale, String expected) {
        assertThat(Rounding.halfUp(value, scale).toPlainString()).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(doubles = {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
    void rejectsNonFiniteValues(double value) {
        assertThatThrownBy(() -> Rounding.halfUp(value, 2)).isInstanceOf(IllegalArgumentException.class);
    }
}
