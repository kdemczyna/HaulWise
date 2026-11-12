package com.haulwise.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.haulwise.HaulWiseApiTest;
import com.haulwise.domain.LegSource;
import com.haulwise.domain.RouteLeg;
import com.haulwise.domain.Settings;
import com.haulwise.domain.Warehouse;
import com.haulwise.repository.RouteLegRepository;
import com.haulwise.repository.SettingsRepository;
import com.haulwise.repository.ShipmentRepository;
import com.haulwise.repository.WarehouseRepository;
import com.haulwise.solver.Haversine;
import com.haulwise.solver.model.DistanceMatrix;
import com.haulwise.solver.model.DistanceSource;
import com.haulwise.solver.model.GeoPoint;
import com.haulwise.solver.model.LegDistance;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

@HaulWiseApiTest
class DistanceServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 8, 0);

    @Autowired
    private DistanceService distanceService;

    @Autowired
    private WarehouseRepository warehouses;

    @Autowired
    private RouteLegRepository routeLegs;

    @Autowired
    private ShipmentRepository shipments;

    @Autowired
    private SettingsRepository settingsRepository;

    private Warehouse waw;
    private Warehouse krk;
    private Warehouse ldz;

    @BeforeEach
    void setUp() {
        routeLegs.deleteAll();
        shipments.deleteAll();
        warehouses.deleteAll();
        settingsRepository.deleteAll();
        waw = warehouses.save(new Warehouse("WAW Central", "Warsaw", 52.2297, 21.0122));
        krk = warehouses.save(new Warehouse("KRK South", "Krakow", 50.0647, 19.9450));
        ldz = warehouses.save(new Warehouse("LDZ Cross-dock", "Lodz", 51.7592, 19.4560));
    }

    @Test
    void storedLegsWinAndMissingPairsAreEstimatedWithDefaultSettings() {
        routeLegs.save(new RouteLeg(waw, krk, 295, 253, LegSource.SEEDED, NOW));
        routeLegs.save(new RouteLeg(krk, waw, 298, 260, LegSource.OSRM, NOW));

        DistanceMatrix matrix = distanceService.buildMatrix(List.of(ldz.getId(), waw.getId(), krk.getId()));

        assertThat(matrix.warehouseIds()).containsExactly(waw.getId(), krk.getId(), ldz.getId());
        assertThat(matrix.distance(waw.getId(), krk.getId()))
                .isEqualTo(new LegDistance(295, 253, DistanceSource.SEEDED));
        assertThat(matrix.distance(krk.getId(), waw.getId()))
                .isEqualTo(new LegDistance(298, 260, DistanceSource.OSRM));
        LegDistance estimated = matrix.distance(waw.getId(), ldz.getId());
        double expectedKm = Haversine.km(point(waw), point(ldz)) * 1.3;
        assertThat(estimated.source()).isEqualTo(DistanceSource.ESTIMATED);
        assertThat(estimated.distanceKm()).isCloseTo(expectedKm, within(1e-9));
        assertThat(estimated.durationMinutes()).isCloseTo(expectedKm / 70 * 60, within(1e-9));
    }

    @Test
    void estimatesUseRoadFactorAndSpeedFromSettings() {
        Settings settings = Settings.withDefaults();
        settings.setRoadFactor(1.5);
        settings.setAverageSpeedKmh(60);
        settingsRepository.save(settings);

        LegDistance estimated = distanceService.buildMatrix(List.of(waw.getId(), krk.getId()))
                .distance(waw.getId(), krk.getId());

        double expectedKm = Haversine.km(point(waw), point(krk)) * 1.5;
        assertThat(estimated.distanceKm()).isCloseTo(expectedKm, within(1e-9));
        assertThat(estimated.durationMinutes()).isCloseTo(expectedKm, within(1e-9));
    }

    @Test
    void estimatesAreNeverStored() {
        distanceService.buildMatrix(List.of(waw.getId(), krk.getId(), ldz.getId()));

        assertThat(routeLegs.count()).isZero();
    }

    @Test
    void duplicateIdsAreCollapsed() {
        DistanceMatrix matrix = distanceService.buildMatrix(List.of(waw.getId(), waw.getId(), krk.getId()));

        assertThat(matrix.warehouseIds()).containsExactly(waw.getId(), krk.getId());
    }

    @Test
    void unknownWarehouseIdIsRejected() {
        assertThatThrownBy(() -> distanceService.buildMatrix(List.of(waw.getId(), Long.MAX_VALUE)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(String.valueOf(Long.MAX_VALUE));
    }

    private static GeoPoint point(Warehouse warehouse) {
        return new GeoPoint(warehouse.getId(), warehouse.getLat(), warehouse.getLon());
    }
}
