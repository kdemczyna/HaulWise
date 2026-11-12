package com.haulwise.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.haulwise.solver.model.DirectedPair;
import com.haulwise.solver.model.DistanceMatrix;
import com.haulwise.solver.model.DistanceSource;
import com.haulwise.solver.model.GeoPoint;
import com.haulwise.solver.model.LegDistance;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class DistanceMatrixBuilderTest {

    private static final GeoPoint WAW = new GeoPoint(1, 52.2297, 21.0122);
    private static final GeoPoint KRK = new GeoPoint(2, 50.0647, 19.9450);
    private static final GeoPoint LDZ = new GeoPoint(3, 51.7592, 19.4560);

    private final DistanceMatrixBuilder builder = new DistanceMatrixBuilder(1.3, 70);

    @Test
    void estimateAppliesRoadFactorAndAverageSpeed() {
        LegDistance estimate = builder.estimate(WAW, KRK);

        double expectedKm = Haversine.km(WAW, KRK) * 1.3;
        assertThat(estimate.distanceKm()).isCloseTo(expectedKm, within(1e-9));
        assertThat(estimate.durationMinutes()).isCloseTo(expectedKm / 70 * 60, within(1e-9));
        assertThat(estimate.source()).isEqualTo(DistanceSource.ESTIMATED);
        assertThat(estimate.isEstimated()).isTrue();
    }

    @Test
    void roadFactorOfOneEqualsGreatCircleDistance() {
        LegDistance estimate = new DistanceMatrixBuilder(1.0, 60).estimate(WAW, KRK);

        assertThat(estimate.distanceKm()).isCloseTo(Haversine.km(WAW, KRK), within(1e-9));
        assertThat(estimate.durationMinutes()).isCloseTo(estimate.distanceKm(), within(1e-9));
    }

    @Test
    void storedLegWinsOverEstimateAndMissingPairsFallBack() {
        LegDistance seeded = new LegDistance(295, 253, DistanceSource.SEEDED);
        LegDistance osrm = new LegDistance(298.4, 262.5, DistanceSource.OSRM);

        DistanceMatrix matrix = builder.build(List.of(WAW, KRK, LDZ), Map.of(
                new DirectedPair(1, 2), seeded,
                new DirectedPair(2, 1), osrm));

        assertThat(matrix.distance(1, 2)).isEqualTo(seeded);
        assertThat(matrix.distance(2, 1)).isEqualTo(osrm);
        assertThat(matrix.distance(1, 3)).isEqualTo(builder.estimate(WAW, LDZ));
        assertThat(matrix.distance(3, 2)).isEqualTo(builder.estimate(LDZ, KRK));
        assertThat(matrix.hasEstimates()).isTrue();
    }

    @Test
    void sameWarehouseIsZeroAndNotAnEstimate() {
        DistanceMatrix matrix = builder.build(List.of(WAW, KRK), Map.of());

        assertThat(matrix.distance(1, 1)).isEqualTo(LegDistance.ZERO);
        assertThat(matrix.distance(1, 1).distanceKm()).isZero();
        assertThat(matrix.distance(1, 1).isEstimated()).isFalse();
    }

    @Test
    void estimatedMatrixIsSymmetricAndComplete() {
        DistanceMatrix matrix = builder.build(List.of(WAW, KRK, LDZ), Map.of());

        assertThat(matrix.legs()).hasSize(6);
        for (DirectedPair pair : matrix.legs().keySet()) {
            assertThat(matrix.distance(pair.fromId(), pair.toId()))
                    .isEqualTo(matrix.distance(pair.toId(), pair.fromId()));
        }
    }

    @Test
    void keepsWarehouseOrderAndIgnoresStoredLegsOfOtherWarehouses() {
        DistanceMatrix matrix = builder.build(List.of(LDZ, WAW), Map.of(
                new DirectedPair(1, 2), new LegDistance(295, 253, DistanceSource.SEEDED)));

        assertThat(matrix.warehouseIds()).containsExactly(3L, 1L);
        assertThat(matrix.legs().keySet()).containsExactly(new DirectedPair(3, 1), new DirectedPair(1, 3));
        assertThat(matrix.hasEstimates()).isTrue();
    }

    @Test
    void singleWarehouseHasNoLegs() {
        DistanceMatrix matrix = builder.build(List.of(WAW), Map.of());

        assertThat(matrix.legs()).isEmpty();
        assertThat(matrix.distance(1, 1)).isEqualTo(LegDistance.ZERO);
    }

    @Test
    void unknownWarehouseIsRejected() {
        DistanceMatrix matrix = builder.build(List.of(WAW, KRK), Map.of());

        assertThatThrownBy(() -> matrix.distance(1, 99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Warehouse 99");
    }

    @Test
    void duplicateWarehouseIdIsRejected() {
        assertThatThrownBy(() -> builder.build(List.of(WAW, new GeoPoint(1, 50.0, 20.0)), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate warehouse id 1");
    }

    @ParameterizedTest(name = "roadFactor={0}, averageSpeedKmh={1}")
    @CsvSource({"0.99, 70", "NaN, 70", "1.3, 0", "1.3, -5", "1.3, Infinity"})
    void invalidEstimateParametersAreRejected(double roadFactor, double averageSpeedKmh) {
        assertThatThrownBy(() -> new DistanceMatrixBuilder(roadFactor, averageSpeedKmh))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
