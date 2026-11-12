package com.haulwise.service;

import com.haulwise.domain.LegSource;
import com.haulwise.domain.Warehouse;
import com.haulwise.dto.DistanceMatrixResponse;
import com.haulwise.repository.RouteLegRepository;
import com.haulwise.repository.StoredLeg;
import com.haulwise.repository.WarehouseRepository;
import com.haulwise.solver.DistanceMatrixBuilder;
import com.haulwise.solver.model.DirectedPair;
import com.haulwise.solver.model.DistanceMatrix;
import com.haulwise.solver.model.DistanceSource;
import com.haulwise.solver.model.GeoPoint;
import com.haulwise.solver.model.LegDistance;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds distance matrices for the solver and the API (ARCHITECTURE 6.6): stored route legs (seeded or OSRM)
 * first, otherwise a haversine x road factor estimate timed at the average speed. Road factor and average
 * speed come from the Settings row. Estimates are never stored (D-06).
 * <p>
 * Not read-only: reading the settings creates the defaults row on first access.
 */
@Service
@Transactional
public class DistanceService {

    private final WarehouseRepository warehouses;
    private final RouteLegRepository routeLegs;
    private final SettingsService settingsService;

    public DistanceService(WarehouseRepository warehouses, RouteLegRepository routeLegs,
                           SettingsService settingsService) {
        this.warehouses = warehouses;
        this.routeLegs = routeLegs;
        this.settingsService = settingsService;
    }

    /**
     * Matrix for the given warehouses, ordered by id.
     *
     * @throws IllegalArgumentException if an id does not exist (callers validate ids first)
     */
    public DistanceMatrix buildMatrix(Collection<Long> warehouseIds) {
        Set<Long> requested = new HashSet<>(warehouseIds);
        List<Warehouse> found = warehouses.findAllById(requested).stream()
                .sorted(Comparator.comparing(Warehouse::getId))
                .toList();
        if (found.size() != requested.size()) {
            Set<Long> missing = new HashSet<>(requested);
            found.forEach(warehouse -> missing.remove(warehouse.getId()));
            throw new IllegalArgumentException("Unknown warehouse ids: " + missing);
        }
        return build(found);
    }

    /** Matrix of all warehouses, ordered by name like the warehouse list. */
    public DistanceMatrixResponse getMatrix() {
        List<Warehouse> all = warehouses.findAll(Sort.by(Sort.Order.asc("name").ignoreCase()));
        return DistanceMatrixResponse.from(all, build(all));
    }

    private DistanceMatrix build(List<Warehouse> ordered) {
        EstimateParameters estimates = settingsService.estimateParameters();
        DistanceMatrixBuilder builder =
                new DistanceMatrixBuilder(estimates.roadFactor(), estimates.averageSpeedKmh());
        List<GeoPoint> points = ordered.stream()
                .map(warehouse -> new GeoPoint(warehouse.getId(), warehouse.getLat(), warehouse.getLon()))
                .toList();
        return builder.build(points, storedLegs(ordered));
    }

    private Map<DirectedPair, LegDistance> storedLegs(List<Warehouse> ordered) {
        if (ordered.size() < 2) {
            return Map.of();
        }
        List<Long> ids = ordered.stream().map(Warehouse::getId).toList();
        return routeLegs.findLegsBetween(ids).stream().collect(Collectors.toMap(
                leg -> new DirectedPair(leg.fromId(), leg.toId()), DistanceService::toLegDistance));
    }

    private static LegDistance toLegDistance(StoredLeg leg) {
        return new LegDistance(leg.distanceKm(), leg.durationMinutes(), toDistanceSource(leg.source()));
    }

    private static DistanceSource toDistanceSource(LegSource source) {
        return switch (source) {
            case SEEDED -> DistanceSource.SEEDED;
            case OSRM -> DistanceSource.OSRM;
        };
    }
}
