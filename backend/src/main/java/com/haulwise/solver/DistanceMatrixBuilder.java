package com.haulwise.solver;

import com.haulwise.solver.model.DirectedPair;
import com.haulwise.solver.model.DistanceMatrix;
import com.haulwise.solver.model.DistanceSource;
import com.haulwise.solver.model.GeoPoint;
import com.haulwise.solver.model.LegDistance;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds a complete {@link DistanceMatrix}: a stored leg (seeded or OSRM) wins; every other pair is
 * estimated as great-circle km x road factor, driven at the average speed (ARCHITECTURE 6.6).
 */
public final class DistanceMatrixBuilder {

    private final double roadFactor;
    private final double averageSpeedKmh;

    public DistanceMatrixBuilder(double roadFactor, double averageSpeedKmh) {
        if (!Double.isFinite(roadFactor) || roadFactor < 1.0) {
            throw new IllegalArgumentException("roadFactor must be >= 1.0, was " + roadFactor);
        }
        if (!Double.isFinite(averageSpeedKmh) || averageSpeedKmh <= 0) {
            throw new IllegalArgumentException("averageSpeedKmh must be > 0, was " + averageSpeedKmh);
        }
        this.roadFactor = roadFactor;
        this.averageSpeedKmh = averageSpeedKmh;
    }

    /**
     * @param points warehouses of the matrix (distinct ids), in output order
     * @param storedLegs known legs; legs touching warehouses outside {@code points} are ignored
     */
    public DistanceMatrix build(List<GeoPoint> points, Map<DirectedPair, LegDistance> storedLegs) {
        Map<Long, GeoPoint> byId = new LinkedHashMap<>();
        for (GeoPoint point : points) {
            if (byId.put(point.warehouseId(), point) != null) {
                throw new IllegalArgumentException("Duplicate warehouse id " + point.warehouseId());
            }
        }
        List<Long> ids = List.copyOf(byId.keySet());
        Map<DirectedPair, LegDistance> legs = new HashMap<>();
        for (DirectedPair pair : DistanceMatrix.allPairs(ids)) {
            LegDistance stored = storedLegs.get(pair);
            legs.put(pair, stored != null ? stored : estimate(byId.get(pair.fromId()), byId.get(pair.toId())));
        }
        return new DistanceMatrix(ids, legs);
    }

    /** Road estimate between two points; symmetric by construction. */
    public LegDistance estimate(GeoPoint from, GeoPoint to) {
        double km = Haversine.km(from, to) * roadFactor;
        return new LegDistance(km, km / averageSpeedKmh * 60, DistanceSource.ESTIMATED);
    }
}
