package com.haulwise.solver.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DistanceMatrixTest {

    private static final LegDistance LEG = new LegDistance(100, 90, DistanceSource.SEEDED);

    @Test
    void completeMatrixAnswersEveryPair() {
        DistanceMatrix matrix = new DistanceMatrix(List.of(1L, 2L), Map.of(
                new DirectedPair(1, 2), LEG,
                new DirectedPair(2, 1), LEG));

        assertThat(matrix.distance(1, 2)).isEqualTo(LEG);
        assertThat(matrix.distance(2, 1)).isEqualTo(LEG);
        assertThat(matrix.hasEstimates()).isFalse();
    }

    @Test
    void missingLegIsRejected() {
        assertThatThrownBy(() -> new DistanceMatrix(List.of(1L, 2L), Map.of(new DirectedPair(1, 2), LEG)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("missing legs");
    }

    @Test
    void legOfAnUnknownWarehouseIsRejected() {
        assertThatThrownBy(() -> new DistanceMatrix(List.of(1L, 2L), Map.of(
                new DirectedPair(1, 2), LEG,
                new DirectedPair(2, 1), LEG,
                new DirectedPair(1, 3), LEG)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("outside");
    }

    @Test
    void duplicateIdsAreRejected() {
        assertThatThrownBy(() -> new DistanceMatrix(List.of(1L, 1L), Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void legsViewIsReadOnly() {
        DistanceMatrix matrix = new DistanceMatrix(List.of(1L), Map.of());

        assertThatThrownBy(() -> matrix.legs().put(new DirectedPair(1, 2), LEG))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void negativeOrNonFiniteLegValuesAreRejected() {
        assertThatThrownBy(() -> new LegDistance(-1, 10, DistanceSource.SEEDED))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LegDistance(10, Double.NaN, DistanceSource.SEEDED))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
