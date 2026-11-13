package com.haulwise.controller;

import static com.haulwise.controller.ApiTestIds.MISSING_ID;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/**
 * {@code POST /api/trucks/recommend} (T-30): picking the cheapest combination of the whole fleet that carries
 * every requested shipment. Default settings (diesel 6.20 PLN/L, 30 min service per stop); the settings row is
 * recreated before every test.
 */
@HaulWiseApiTest
@AutoConfigureMockMvc
class TruckRecommendationApiTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mvc;

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

    private Warehouse depot;
    private Warehouse bstadt;

    @BeforeEach
    void cleanDatabaseAndCreateDepot() {
        routeLegs.deleteAll();
        shipments.deleteAll();
        trucks.deleteAll();
        warehouses.deleteAll();
        settings.deleteAll();
        depot = warehouses.save(new Warehouse("Depot A", "Astadt", 52.0, 21.0));
        bstadt = warehouses.save(new Warehouse("B Hub", "Bstadt", 51.0, 20.0));
        storeLegBothWays(depot, bstadt, 100, 90);
    }

    /**
     * 35 pallets across three shipments; a 35-pallet truck is available alongside two smaller trucks that
     * would also fit everything between them. All three trucks have the same running costs, so the single
     * truck is both cheaper (one trip instead of two) and fewer trucks - no conflict between cost and truck
     * count here; see {@link #cheaperTwoTruckCombinationBeatsAMoreExpensiveSingleTruck} for that case.
     */
    @Test
    void exactFitRecommendationPicksTheSingleMatchingTruckOverTwoSmallerTrucks() throws Exception {
        Truck big = trucks.save(new Truck("WX 3003C", 35, 24000, 28, 10, 45, depot));
        // Together these two also fit everything, but two trips with equal running costs cost more.
        trucks.save(new Truck("WX 1001A", 20, 24000, 28, 10, 45, depot));
        trucks.save(new Truck("WX 2002B", 15, 24000, 28, 10, 45, depot));
        List<Shipment> load = List.of(
                shipment("PO-1", 20, 2000),
                shipment("PO-2", 10, 1000),
                shipment("PO-3", 5, 500));

        recommend(request(depot.getId(), ids(load), true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.truckIds", contains(big.getId().intValue())))
                .andExpect(jsonPath("$.trucks", hasSize(1)))
                .andExpect(jsonPath("$.trucks[0].id").value(big.getId()))
                .andExpect(jsonPath("$.trucks[0].plate").value("WX 3003C"))
                .andExpect(jsonPath("$.utilisationPct").value(100.0))
                .andExpect(jsonPath("$.totals.costPln").isNumber())
                .andExpect(jsonPath("$.unassigned", hasSize(0)));
    }

    /**
     * Cost beats truck count: the 35-pallet truck carries everything alone but burns 40 + 15 L/100 km and
     * costs 120 PLN/h, while the two small trucks (15 + 3 L/100 km, 20 PLN/h each) carry the same 35 pallets
     * between them for roughly half the total cost. The two-truck combination is recommended.
     */
    @Test
    void cheaperTwoTruckCombinationBeatsAMoreExpensiveSingleTruck() throws Exception {
        trucks.save(new Truck("WX 3003C", 35, 24000, 40, 15, 120, depot));
        Truck smallA = trucks.save(new Truck("WX 1001A", 20, 24000, 15, 3, 20, depot));
        Truck smallB = trucks.save(new Truck("WX 2002B", 15, 24000, 15, 3, 20, depot));
        List<Shipment> load = List.of(
                shipment("PO-1", 20, 2000),
                shipment("PO-2", 10, 1000),
                shipment("PO-3", 5, 500));

        recommend(request(depot.getId(), ids(load), true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.truckIds",
                        contains(smallA.getId().intValue(), smallB.getId().intValue())))
                .andExpect(jsonPath("$.trucks[*].plate", contains("WX 1001A", "WX 2002B")))
                .andExpect(jsonPath("$.utilisationPct").value(100.0))
                .andExpect(jsonPath("$.unassigned", hasSize(0)));
    }

    /** No single truck can take 40 pallets; the cheapest 2-truck combination is recommended. */
    @Test
    void twoTrucksAreRecommendedWhenNoSingleTruckFitsEverything() throws Exception {
        trucks.save(new Truck("WX 1001A", 25, 24000, 28, 10, 45, depot));
        trucks.save(new Truck("WX 2002B", 20, 24000, 28, 10, 45, depot));
        List<Shipment> load = List.of(
                shipment("PO-1", 25, 2000),
                shipment("PO-2", 15, 1500));

        recommend(request(depot.getId(), ids(load), true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.truckIds", hasSize(2)))
                .andExpect(jsonPath("$.trucks", hasSize(2)))
                .andExpect(jsonPath("$.trucks[*].plate", containsInAnyOrder("WX 1001A", "WX 2002B")))
                .andExpect(jsonPath("$.unassigned", hasSize(0)));
    }

    /** Heavier than the largest truck's payload: reported as unassigned, not a 500. */
    @Test
    void shipmentThatFitsNoTruckIsReportedAsInfeasibleNotA500() throws Exception {
        trucks.save(new Truck("WX 1001A", 33, 6000, 28, 10, 45, depot));
        Shipment tooHeavy = shipment("PO-99", 5, 30000);

        recommend(request(depot.getId(), List.of(tooHeavy.getId()), true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.truckIds", hasSize(0)))
                .andExpect(jsonPath("$.trucks", hasSize(0)))
                .andExpect(jsonPath("$.totals").doesNotExist())
                .andExpect(jsonPath("$.utilisationPct").doesNotExist())
                .andExpect(jsonPath("$.unassigned", hasSize(1)))
                .andExpect(jsonPath("$.unassigned[0].shipmentId").value(tooHeavy.getId()))
                .andExpect(jsonPath("$.unassigned[0].reference").value("PO-99"))
                .andExpect(jsonPath("$.unassigned[0].reason").value("EXCEEDS_LARGEST_TRUCK"))
                .andExpect(jsonPath("$.unassigned[0].message")
                        .value("30000 kg exceed the largest payload of the selected trucks (6000 kg)."));
    }

    /** No trucks at all: every shipment is unassigned ("No trucks are selected."), not a 500. */
    @Test
    void noTrucksAtAllIsReportedAsInfeasible() throws Exception {
        Shipment shipment = shipment("PO-1", 5, 500);

        recommend(request(depot.getId(), List.of(shipment.getId()), true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.truckIds", hasSize(0)))
                .andExpect(jsonPath("$.unassigned[0].message").value("No trucks are selected."));
    }

    @Test
    void missingFieldsAndEmptyListsReturn400WithAllFieldErrors() throws Exception {
        recommend("{\"shipmentIds\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].field").value("depotId"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be null"))
                .andExpect(jsonPath("$.errors[1].field").value("shipmentIds"))
                .andExpect(jsonPath("$.errors[1].message").value("size must be between 1 and 200"));
    }

    @Test
    void unknownDepotAndShipmentReturn400() throws Exception {
        recommend(request(MISSING_ID, List.of(MISSING_ID), true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].field").value("depotId"))
                .andExpect(jsonPath("$.errors[0].message").value("warehouse " + MISSING_ID + " does not exist"))
                .andExpect(jsonPath("$.errors[1].field").value("shipmentIds"))
                .andExpect(jsonPath("$.errors[1].message").value("shipment " + MISSING_ID + " does not exist"));
    }

    @Test
    void shipmentFromAnotherDepotReturns400NamingIt() throws Exception {
        trucks.save(new Truck("WX 1001A", 33, 24000, 28, 10, 45, depot));
        Shipment fromElsewhere = shipments.save(new Shipment("PO-9", bstadt, depot, 4, 1000));

        recommend(request(depot.getId(), List.of(fromElsewhere.getId()), true))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("shipmentIds"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("PO-9 (id " + fromElsewhere.getId() + ") does not start at depot Depot A"));
    }

    /**
     * T-32 + T-33: a much cheaper truck based at another depot and a cheaper truck on route at this depot are
     * never recommended; only this depot's available truck is.
     */
    @Test
    void onlyAvailableTrucksBasedAtTheDepotAreCandidates() throws Exception {
        Truck own = trucks.save(new Truck("WX 1001A", 33, 24000, 40, 15, 120, depot));
        trucks.save(new Truck("PO 6006F", 33, 24000, 10, 1, 5, bstadt));
        Truck onRoute = new Truck("WX 2002B", 33, 24000, 10, 1, 5, depot);
        onRoute.setAvailable(false);
        trucks.save(onRoute);
        Shipment shipment = shipment("PO-1", 10, 1000);

        recommend(request(depot.getId(), List.of(shipment.getId()), true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.truckIds", contains(own.getId().intValue())))
                .andExpect(jsonPath("$.trucks[*].plate", contains("WX 1001A")));
    }

    @Test
    void depotWithoutAvailableTrucksIsInfeasibleEvenWhenOtherDepotsHaveSome() throws Exception {
        trucks.save(new Truck("PO 6006F", 33, 24000, 28, 10, 45, bstadt));
        Shipment shipment = shipment("PO-1", 10, 1000);

        recommend(request(depot.getId(), List.of(shipment.getId()), true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.truckIds", hasSize(0)))
                .andExpect(jsonPath("$.unassigned[0].shipmentId").value(shipment.getId()));
    }

    private ResultActions recommend(String json) throws Exception {
        return mvc.perform(post("/api/trucks/recommend").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void storeLegBothWays(Warehouse a, Warehouse b, double km, double minutes) {
        LocalDateTime updated = LocalDateTime.of(2026, 11, 12, 8, 0);
        routeLegs.save(new RouteLeg(a, b, km, minutes, LegSource.SEEDED, updated));
        routeLegs.save(new RouteLeg(b, a, km, minutes, LegSource.SEEDED, updated));
    }

    private Shipment shipment(String reference, int pallets, int weightKg) {
        Shipment shipment = new Shipment(reference, depot, bstadt, pallets, weightKg);
        shipment.setPriority(Priority.NORMAL);
        return shipments.save(shipment);
    }

    private static List<Long> ids(List<Shipment> shipments) {
        return shipments.stream().map(Shipment::getId).toList();
    }

    private static String request(Long depotId, List<Long> shipmentIds, Boolean returnToDepot) {
        return """
                {"depotId": %d, "shipmentIds": %s, "returnToDepot": %s}
                """.formatted(depotId, idsJson(shipmentIds), returnToDepot);
    }

    private static String idsJson(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(", ", "[", "]"));
    }
}
