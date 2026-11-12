package com.haulwise.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haulwise.HaulWiseApiTest;
import com.haulwise.domain.LegSource;
import com.haulwise.domain.RouteLeg;
import com.haulwise.domain.Warehouse;
import com.haulwise.repository.RouteLegRepository;
import com.haulwise.repository.SettingsRepository;
import com.haulwise.repository.ShipmentRepository;
import com.haulwise.repository.WarehouseRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

@HaulWiseApiTest
@AutoConfigureMockMvc
class DistanceApiTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 8, 0);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private WarehouseRepository warehouses;

    @Autowired
    private RouteLegRepository routeLegs;

    @Autowired
    private ShipmentRepository shipments;

    @Autowired
    private SettingsRepository settingsRepository;

    @BeforeEach
    void cleanDatabase() {
        routeLegs.deleteAll();
        shipments.deleteAll();
        warehouses.deleteAll();
        settingsRepository.deleteAll();
    }

    @Test
    void emptyDatabaseGivesEmptyMatrix() throws Exception {
        mvc.perform(get("/api/distances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warehouses", hasSize(0)))
                .andExpect(jsonPath("$.legs", hasSize(0)));
    }

    @Test
    void returnsCompleteMatrixWithStoredLegsAndRoundedEstimates() throws Exception {
        Warehouse waw = warehouses.save(new Warehouse("WAW Central", "Warsaw", 52.2297, 21.0122));
        Warehouse krk = warehouses.save(new Warehouse("KRK South", "Krakow", 50.0647, 19.9450));
        routeLegs.save(new RouteLeg(waw, krk, 295, 253, LegSource.SEEDED, NOW));
        routeLegs.save(new RouteLeg(krk, waw, 298.46, 262.6, LegSource.OSRM, NOW));
        Warehouse ldz = warehouses.save(new Warehouse("LDZ Cross-dock", "Lodz", 51.7592, 19.4560));

        // Sorted by name: KRK, LDZ, WAW; legs row by row in that order.
        mvc.perform(get("/api/distances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warehouses", hasSize(3)))
                .andExpect(jsonPath("$.warehouses[0].id").value(krk.getId()))
                .andExpect(jsonPath("$.warehouses[0].name").value("KRK South"))
                .andExpect(jsonPath("$.warehouses[1].name").value("LDZ Cross-dock"))
                .andExpect(jsonPath("$.warehouses[2].name").value("WAW Central"))
                .andExpect(jsonPath("$.legs", hasSize(6)))
                .andExpect(jsonPath("$.legs[0].fromId").value(krk.getId()))
                .andExpect(jsonPath("$.legs[0].toId").value(ldz.getId()))
                .andExpect(jsonPath("$.legs[0].source").value("ESTIMATED"))
                .andExpect(jsonPath("$.legs[1].fromId").value(krk.getId()))
                .andExpect(jsonPath("$.legs[1].toId").value(waw.getId()))
                .andExpect(jsonPath("$.legs[1].distanceKm").value(298.5))
                .andExpect(jsonPath("$.legs[1].durationMinutes").value(263))
                .andExpect(jsonPath("$.legs[1].source").value("OSRM"))
                .andExpect(jsonPath("$.legs[4].fromId").value(waw.getId()))
                .andExpect(jsonPath("$.legs[4].toId").value(krk.getId()))
                .andExpect(jsonPath("$.legs[4].distanceKm").value(295.0))
                .andExpect(jsonPath("$.legs[4].durationMinutes").value(253))
                .andExpect(jsonPath("$.legs[4].source").value("SEEDED"))
                .andExpect(jsonPath("$.legs[?(@.source == 'ESTIMATED')]", hasSize(4)));
    }

    @Test
    void estimateUsesDefaultRoadFactorAndSpeed() throws Exception {
        Warehouse waw = warehouses.save(new Warehouse("WAW Central", "Warsaw", 52.2297, 21.0122));
        warehouses.save(new Warehouse("KRK South", "Krakow", 50.0647, 19.9450));

        // Great circle ~252.0 km x 1.3 = ~327.6 km; at 70 km/h = ~281 min.
        mvc.perform(get("/api/distances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.legs[1].fromId").value(waw.getId()))
                .andExpect(jsonPath("$.legs[1].distanceKm").value(327.6))
                .andExpect(jsonPath("$.legs[1].durationMinutes").value(281));
    }
}
