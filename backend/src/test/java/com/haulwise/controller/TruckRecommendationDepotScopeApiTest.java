package com.haulwise.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haulwise.HaulWiseApiTest;
import com.haulwise.domain.LegSource;
import com.haulwise.domain.RouteLeg;
import com.haulwise.domain.Shipment;
import com.haulwise.domain.Truck;
import com.haulwise.domain.Warehouse;
import com.haulwise.repository.RouteLegRepository;
import com.haulwise.repository.SettingsRepository;
import com.haulwise.repository.ShipmentRepository;
import com.haulwise.repository.TruckRepository;
import com.haulwise.repository.WarehouseRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Scaling of {@code POST /api/trucks/recommend} (T-32): the subset search is bounded by the requested depot's own
 * fleet, not the whole truck table. The cap is 3 here; depot A has exactly 3 trucks, depot B has 10 trucks that are
 * bigger and far cheaper. If the search saw the whole 13-truck table it would exceed the cap and fall back to the
 * greedy search, which keeps the first truck that packs everything (one of B's 40-pallet trucks); an exhaustive
 * search of the whole table would pick B's cheap trucks too. Scoped to A, the exhaustive search runs on 3 trucks
 * (7 subsets) and finds A's cheapest combination: the two small trucks - which the greedy fallback can never return,
 * since it only drops trucks from a packing that loads the biggest truck first.
 */
@HaulWiseApiTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "haulwise.solver.max-recommendation-trucks=3")
class TruckRecommendationDepotScopeApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private WarehouseRepository warehouses;

    @Autowired
    private RouteLegRepository routeLegs;

    @Autowired
    private ShipmentRepository shipments;

    @Autowired
    private TruckRepository trucks;

    @Autowired
    private SettingsRepository settings;

    @Test
    void aBigFleetAtAnotherDepotNeitherLeaksInNorPushesTheSmallDepotOverTheSearchCap() throws Exception {
        routeLegs.deleteAll();
        shipments.deleteAll();
        trucks.deleteAll();
        warehouses.deleteAll();
        settings.deleteAll();
        Warehouse small = warehouses.save(new Warehouse("Depot A", "Astadt", 52.0, 21.0));
        Warehouse large = warehouses.save(new Warehouse("Depot B", "Bstadt", 51.0, 20.0));
        Warehouse customer = warehouses.save(new Warehouse("C Hub", "Cstadt", 51.5, 19.0));
        LocalDateTime updated = LocalDateTime.of(2026, 11, 12, 8, 0);
        routeLegs.save(new RouteLeg(small, customer, 100, 90, LegSource.SEEDED, updated));
        routeLegs.save(new RouteLeg(customer, small, 100, 90, LegSource.SEEDED, updated));
        trucks.save(new Truck("WX 3003C", 35, 24000, 40, 15, 120, small));
        Truck smallA = trucks.save(new Truck("WX 1001A", 20, 24000, 15, 3, 20, small));
        Truck smallB = trucks.save(new Truck("WX 2002B", 15, 24000, 15, 3, 20, small));
        for (int i = 0; i < 10; i++) {
            trucks.save(new Truck("PO 900" + i + "X", 40, 40000, 5, 1, 5, large));
        }
        List<Long> load = List.of(
                shipments.save(new Shipment("PO-1", small, customer, 20, 2000)).getId(),
                shipments.save(new Shipment("PO-2", small, customer, 10, 1000)).getId(),
                shipments.save(new Shipment("PO-3", small, customer, 5, 500)).getId());

        long started = System.nanoTime();
        mvc.perform(post("/api/trucks/recommend").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"depotId\": %d, \"shipmentIds\": %s}".formatted(small.getId(), json(load))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.truckIds", contains(smallA.getId().intValue(), smallB.getId().intValue())))
                .andExpect(jsonPath("$.trucks[*].plate", contains("WX 1001A", "WX 2002B")))
                .andExpect(jsonPath("$.unassigned", hasSize(0)));
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        // Generous bound for slow machines; the scoped 7-subset search takes a few milliseconds.
        assertThat(elapsedMs).isLessThan(5_000);
    }

    private static String json(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(", ", "[", "]"));
    }
}
