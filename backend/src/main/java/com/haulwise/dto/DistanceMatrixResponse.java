package com.haulwise.dto;

import com.haulwise.domain.Warehouse;
import com.haulwise.solver.model.DirectedPair;
import com.haulwise.solver.model.DistanceMatrix;
import com.haulwise.solver.model.DistanceSource;
import com.haulwise.solver.model.LegDistance;
import java.util.List;

/**
 * Complete n x (n-1) matrix of all warehouses; pairs without a stored leg are estimates.
 * Kilometres are rounded to 1 decimal and minutes to whole minutes.
 */
public record DistanceMatrixResponse(List<WarehouseRef> warehouses, List<Leg> legs) {

    public record WarehouseRef(Long id, String name) {
    }

    public record Leg(Long fromId, Long toId, double distanceKm, long durationMinutes, DistanceSource source) {

        static Leg from(DirectedPair pair, LegDistance leg) {
            return new Leg(pair.fromId(), pair.toId(), Math.round(leg.distanceKm() * 10) / 10.0,
                    Math.round(leg.durationMinutes()), leg.source());
        }
    }

    /**
     * @param warehouses the matrix's warehouses, in the order of {@link DistanceMatrix#warehouseIds()}
     * @throws IllegalArgumentException if the warehouses do not match the matrix ids and order
     */
    public static DistanceMatrixResponse from(List<Warehouse> warehouses, DistanceMatrix matrix) {
        List<Long> ids = warehouses.stream().map(Warehouse::getId).toList();
        if (!ids.equals(matrix.warehouseIds())) {
            throw new IllegalArgumentException(
                    "Warehouses " + ids + " do not match the matrix order " + matrix.warehouseIds());
        }
        List<WarehouseRef> refs = warehouses.stream()
                .map(warehouse -> new WarehouseRef(warehouse.getId(), warehouse.getName()))
                .toList();
        List<Leg> legs = matrix.legs().entrySet().stream()
                .map(entry -> Leg.from(entry.getKey(), entry.getValue()))
                .toList();
        return new DistanceMatrixResponse(refs, legs);
    }
}
