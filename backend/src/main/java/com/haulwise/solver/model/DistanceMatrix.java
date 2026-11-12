package com.haulwise.solver.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable, complete distance matrix for a fixed set of warehouses, indexed by warehouse id:
 * every ordered pair of distinct warehouses has exactly one leg.
 */
public final class DistanceMatrix implements DistanceProvider {

    private final List<Long> warehouseIds;
    private final Set<Long> knownIds;
    private final Map<DirectedPair, LegDistance> legs;

    /**
     * @param warehouseIds distinct warehouse ids; their order is kept for {@link #warehouseIds()} and {@link #legs()}
     * @param legs a leg for every ordered pair of distinct ids and for no other pair
     */
    public DistanceMatrix(List<Long> warehouseIds, Map<DirectedPair, LegDistance> legs) {
        if (warehouseIds.stream().distinct().count() != warehouseIds.size()) {
            throw new IllegalArgumentException("Warehouse ids must be distinct: " + warehouseIds);
        }
        this.warehouseIds = List.copyOf(warehouseIds);
        this.knownIds = Set.copyOf(this.warehouseIds);
        Map<DirectedPair, LegDistance> ordered = new LinkedHashMap<>();
        List<DirectedPair> missing = new ArrayList<>();
        for (DirectedPair pair : allPairs(this.warehouseIds)) {
            LegDistance leg = legs.get(pair);
            if (leg == null) {
                missing.add(pair);
            } else {
                ordered.put(pair, leg);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("Distance matrix is missing legs: " + missing);
        }
        if (legs.size() != ordered.size()) {
            throw new IllegalArgumentException("Distance matrix has legs for warehouses outside " + warehouseIds);
        }
        this.legs = ordered;
    }

    /** All ordered pairs of distinct ids, row by row in the order of the given list. */
    public static List<DirectedPair> allPairs(List<Long> warehouseIds) {
        List<DirectedPair> pairs = new ArrayList<>();
        for (long from : warehouseIds) {
            for (long to : warehouseIds) {
                if (from != to) {
                    pairs.add(new DirectedPair(from, to));
                }
            }
        }
        return pairs;
    }

    public List<Long> warehouseIds() {
        return warehouseIds;
    }

    @Override
    public LegDistance distance(long fromId, long toId) {
        requireKnown(fromId);
        requireKnown(toId);
        return fromId == toId ? LegDistance.ZERO : legs.get(new DirectedPair(fromId, toId));
    }

    /** Every leg between distinct warehouses, row by row in {@link #warehouseIds()} order (read-only view). */
    public Map<DirectedPair, LegDistance> legs() {
        return Collections.unmodifiableMap(legs);
    }

    public boolean hasEstimates() {
        return legs.values().stream().anyMatch(LegDistance::isEstimated);
    }

    private void requireKnown(long warehouseId) {
        if (!knownIds.contains(warehouseId)) {
            throw new IllegalArgumentException("Warehouse " + warehouseId + " is not in the distance matrix");
        }
    }
}
