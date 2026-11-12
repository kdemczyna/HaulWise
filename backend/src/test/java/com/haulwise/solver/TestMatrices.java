package com.haulwise.solver;

import com.haulwise.solver.model.DirectedPair;
import com.haulwise.solver.model.DistanceMatrix;
import com.haulwise.solver.model.DistanceSource;
import com.haulwise.solver.model.LegDistance;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Hand-checkable distance matrices for solver tests; leg minutes always equal the km. */
final class TestMatrices {

    private TestMatrices() {
    }

    /** Warehouse i + 1 at position x[i] on a line, 10 km per unit. */
    static DistanceMatrix lineMatrix(double... x) {
        Map<DirectedPair, Double> km = new HashMap<>();
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x.length; j++) {
                if (i != j) {
                    km.put(new DirectedPair(i + 1, j + 1), 10 * Math.abs(x[i] - x[j]));
                }
            }
        }
        return matrix(km);
    }

    /** A matrix with the given km per directed pair, all legs ESTIMATED. */
    static DistanceMatrix matrix(Map<DirectedPair, Double> km) {
        Map<DirectedPair, LegDistance> legs = new HashMap<>();
        km.forEach((pair, distance) -> legs.put(pair, new LegDistance(distance, distance, DistanceSource.ESTIMATED)));
        List<Long> ids = km.keySet().stream().map(DirectedPair::fromId).distinct().sorted().toList();
        return new DistanceMatrix(ids, legs);
    }
}
