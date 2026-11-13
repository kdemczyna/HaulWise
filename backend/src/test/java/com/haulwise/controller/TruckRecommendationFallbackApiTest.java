package com.haulwise.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haulwise.HaulWiseApiTest;
import com.haulwise.domain.LegSource;
import com.haulwise.domain.Priority;
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
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code POST /api/trucks/recommend} above the fleet-subset cap (T-30): the exhaustive search is skipped and
 * the greedy fallback is used instead.
 *
 * <p>This was originally a {@code static} nested class inside {@link TruckRecommendationApiTest}, with no
 * {@code @Nested} annotation. It compiled, Surefire's default include pattern matched its name (it ends in
 * "Test"), and a stale {@code target/surefire-reports} XML from an earlier, manually-targeted run made it
 * look like it had run and passed in a later review. None of that means JUnit 5 actually discovered and
 * executed it as part of {@code mvnw verify}: a bare {@code static class} with no {@code @Nested} is not
 * picked up when its enclosing class is run, and it was never selected as a class in its own right either, so
 * it silently never ran. Confirmed with a mutation test: deliberately breaking its assertion did not fail a
 * clean {@code mvnw clean verify}, proving no code path exercised it. Promoted here to a plain top-level
 * class, which Surefire and JUnit 5 both discover and run unambiguously; verified the same way afterward
 * (breaking the assertion now does fail the build).
 */
@HaulWiseApiTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "haulwise.solver.max-recommendation-trucks=1")
class TruckRecommendationFallbackApiTest {

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

    /**
     * Above the cap (set to 1 here), the greedy fallback is used instead of exhaustive search. One truck
     * already carries everything alone, so the fallback's first (trivial) answer matches what exhaustive
     * search would have found - this exercises the fallback code path itself, not a case where it differs
     * from the optimum (the documented limitation).
     */
    @Test
    void aboveTheCapTheGreedyFallbackStillFindsAFeasibleCombination() throws Exception {
        routeLegs.deleteAll();
        shipments.deleteAll();
        trucks.deleteAll();
        warehouses.deleteAll();
        settings.deleteAll();
        Warehouse depot = warehouses.save(new Warehouse("Depot A", "Astadt", 52.0, 21.0));
        Warehouse bstadt = warehouses.save(new Warehouse("B Hub", "Bstadt", 51.0, 20.0));
        routeLegs.save(new RouteLeg(depot, bstadt, 100, 90, LegSource.SEEDED, LocalDateTime.now()));
        routeLegs.save(new RouteLeg(bstadt, depot, 100, 90, LegSource.SEEDED, LocalDateTime.now()));
        Truck big = trucks.save(new Truck("WX 3003C", 35, 24000, 28, 10, 45, depot));
        trucks.save(new Truck("WX 1001A", 20, 24000, 28, 10, 45, depot));
        Shipment shipment = new Shipment("PO-1", depot, bstadt, 20, 2000);
        shipment.setPriority(Priority.NORMAL);
        shipment = shipments.save(shipment);

        mvc.perform(post("/api/trucks/recommend").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"depotId": %d, "shipmentIds": [%d], "returnToDepot": true}
                                """.formatted(depot.getId(), shipment.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.truckIds", contains(big.getId().intValue())))
                .andExpect(jsonPath("$.unassigned", hasSize(0)));
    }
}
