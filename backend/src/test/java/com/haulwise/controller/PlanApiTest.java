package com.haulwise.controller;

import static com.haulwise.controller.ApiTestIds.MISSING_ID;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haulwise.HaulWiseApiTest;
import com.haulwise.config.ClockConfig;
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
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.LongStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * {@code POST /api/plans} and {@code GET /api/plans/{id}} on small hand-computed fixtures. Default settings
 * (diesel 6.20 PLN/L, 30 min service per stop); the settings row is recreated before every test.
 */
@HaulWiseApiTest
@AutoConfigureMockMvc
class PlanApiTest {

    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 11, 12, 9, 30, 0, 0, ClockConfig.ZONE);
    private static final LocalDateTime DEPARTURE = LocalDateTime.of(2026, 11, 13, 6, 0);
    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @TestConfiguration
    static class FixedClock {

        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(NOW.toInstant(), ClockConfig.ZONE);
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

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
    private Truck truck;

    @BeforeEach
    void cleanDatabaseAndCreateDepot() {
        routeLegs.deleteAll();
        shipments.deleteAll();
        trucks.deleteAll();
        warehouses.deleteAll();
        settings.deleteAll();
        depot = warehouses.save(new Warehouse("Depot A", "Astadt", 52.0, 21.0));
        bstadt = warehouses.save(new Warehouse("B Hub", "Bstadt", 51.0, 20.0));
        truck = trucks.save(new Truck("WX 1001A", 33, 24000, 28, 10, 45, depot));
    }

    /**
     * One truck, one 12 000 kg drop at Bstadt over a stored 123.45 km / 100.5 min leg (both ways). Out: half load
     * -> 28 + 10 x 0.5 = 33 L/100 km -> 40.7385 L, 252.5787 PLN fuel, (100.5 + 30) / 60 x 45 = 97.875 PLN driver.
     * Back: empty -> 34.566 L, 214.3092 PLN fuel, 75.375 PLN driver. Both variants are identical.
     */
    @Test
    void createReturns201WithRoundedNumbersAndGetReturnsTheSamePlan() throws Exception {
        storeLegBothWays(depot, bstadt, 123.45, 100.5);
        Shipment shipment = shipment("PO-1", bstadt, 10, 12000, Priority.NORMAL, false, null);

        String body = createPlan(request(depot.getId(), List.of(shipment.getId()), List.of(truck.getId()), null,
                DEPARTURE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.createdAt").value("2026-11-12T09:30:00"))
                .andExpect(jsonPath("$.dispatchedAt").value(nullValue()))
                .andExpect(jsonPath("$.request.returnToDepot").value(true))
                .andExpect(jsonPath("$.request.departureTime").value("2026-11-13T06:00:00"))
                .andExpect(jsonPath("$.depot.name").value("Depot A"))
                .andExpect(jsonPath("$.depot.lat").value(52.0))
                .andExpect(jsonPath("$.optimal.mode").value("OPTIMAL"))
                .andExpect(jsonPath("$.priority.mode").value("PRIORITY"))
                .andExpect(jsonPath("$.optimal.totals.distanceKm").value(246.9))
                .andExpect(jsonPath("$.optimal.totals.drivingMinutes").value(201))
                .andExpect(jsonPath("$.optimal.totals.totalMinutes").value(231))
                .andExpect(jsonPath("$.optimal.totals.litres").value(75.3))
                .andExpect(jsonPath("$.optimal.totals.fuelCostPln").value(466.89))
                .andExpect(jsonPath("$.optimal.totals.driverCostPln").value(173.25))
                .andExpect(jsonPath("$.optimal.totals.costPln").value(640.14))
                .andExpect(jsonPath("$.optimal.deadlinesMissed").value(0))
                .andExpect(jsonPath("$.optimal.warnings", hasSize(0)))
                .andExpect(jsonPath("$.optimal.routes", hasSize(1)))
                .andExpect(jsonPath("$.optimal.routes[0].truckId").value(truck.getId()))
                .andExpect(jsonPath("$.optimal.routes[0].plate").value("WX 1001A"))
                .andExpect(jsonPath("$.optimal.routes[0].colorIndex").value(0))
                .andExpect(jsonPath("$.optimal.routes[0].utilisation.pallets").value(10))
                .andExpect(jsonPath("$.optimal.routes[0].utilisation.capacityPallets").value(33))
                .andExpect(jsonPath("$.optimal.routes[0].utilisation.weightKg").value(12000))
                .andExpect(jsonPath("$.optimal.routes[0].utilisation.payloadKg").value(24000))
                .andExpect(jsonPath("$.optimal.routes[0].utilisation.palletsPct").value(30.3))
                .andExpect(jsonPath("$.optimal.routes[0].utilisation.weightPct").value(50.0))
                .andExpect(jsonPath("$.optimal.routes[0].departureTime").value("2026-11-13T06:00:00"))
                .andExpect(jsonPath("$.optimal.routes[0].returnTime").value("2026-11-13T09:51:00"))
                .andExpect(jsonPath("$.optimal.routes[0].stops", hasSize(1)))
                .andExpect(jsonPath("$.optimal.routes[0].stops[0].sequence").value(1))
                .andExpect(jsonPath("$.optimal.routes[0].stops[0].warehouseId").value(bstadt.getId()))
                .andExpect(jsonPath("$.optimal.routes[0].stops[0].name").value("B Hub"))
                .andExpect(jsonPath("$.optimal.routes[0].stops[0].city").value("Bstadt"))
                .andExpect(jsonPath("$.optimal.routes[0].stops[0].lat").value(51.0))
                .andExpect(jsonPath("$.optimal.routes[0].stops[0].shipmentIds", contains(shipment.getId().intValue())))
                .andExpect(jsonPath("$.optimal.routes[0].stops[0].dropPallets").value(10))
                .andExpect(jsonPath("$.optimal.routes[0].stops[0].dropWeightKg").value(12000))
                .andExpect(jsonPath("$.optimal.routes[0].stops[0].priority").value("NORMAL"))
                .andExpect(jsonPath("$.optimal.routes[0].stops[0].pinned").value(false))
                .andExpect(jsonPath("$.optimal.routes[0].stops[0].legDistanceKm").value(123.5))
                .andExpect(jsonPath("$.optimal.routes[0].stops[0].legDurationMinutes").value(101))
                .andExpect(jsonPath("$.optimal.routes[0].stops[0].legLitres").value(40.7))
                .andExpect(jsonPath("$.optimal.routes[0].stops[0].legCostPln").value(350.45))
                .andExpect(jsonPath("$.optimal.routes[0].stops[0].legSource").value("SEEDED"))
                .andExpect(jsonPath("$.optimal.routes[0].stops[0].loadWeightKgOnLeg").value(12000))
                .andExpect(jsonPath("$.optimal.routes[0].stops[0].arrivalTime").value("2026-11-13T07:41:00"))
                .andExpect(jsonPath("$.optimal.routes[0].stops[0].departureTime").value("2026-11-13T08:11:00"))
                .andExpect(jsonPath("$.optimal.routes[0].stops[0].deadline").value(nullValue()))
                .andExpect(jsonPath("$.optimal.routes[0].stops[0].deadlineMissed").value(false))
                .andExpect(jsonPath("$.optimal.routes[0].returnLeg.distanceKm").value(123.5))
                .andExpect(jsonPath("$.optimal.routes[0].returnLeg.durationMinutes").value(101))
                .andExpect(jsonPath("$.optimal.routes[0].returnLeg.litres").value(34.6))
                .andExpect(jsonPath("$.optimal.routes[0].returnLeg.costPln").value(289.68))
                .andExpect(jsonPath("$.optimal.routes[0].returnLeg.source").value("SEEDED"))
                .andExpect(jsonPath("$.optimal.routes[0].totals.costPln").value(640.14))
                .andExpect(jsonPath("$.optimal.routes[0].warnings", hasSize(0)))
                .andExpect(jsonPath("$.priority.totals.costPln").value(640.14))
                .andExpect(jsonPath("$.delta.distanceKm").value(0.0))
                .andExpect(jsonPath("$.delta.durationMinutes").value(0))
                .andExpect(jsonPath("$.delta.costPln").value(0.0))
                .andExpect(jsonPath("$.delta.noExtraCost").value(true))
                .andExpect(jsonPath("$.delta.sentence").value("Respecting priorities costs nothing extra."))
                .andExpect(jsonPath("$.unassigned", hasSize(0)))
                .andExpect(jsonPath("$.warnings", hasSize(0)))
                .andReturn().getResponse().getContentAsString();
        String id = objectMapper.readTree(body).get("id").asText();

        mvc.perform(get("/api/plans/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.optimal.totals.costPln").value(640.14))
                .andExpect(jsonPath("$.delta.sentence").value("Respecting priorities costs nothing extra."));
    }

    @Test
    void createSetsLocationToTheStoredPlan() throws Exception {
        storeLegBothWays(depot, bstadt, 100, 90);
        Shipment shipment = shipment("PO-1", bstadt, 10, 12000, Priority.NORMAL, false, null);

        String location = createPlan(request(depot.getId(), List.of(shipment.getId()), List.of(truck.getId()),
                null, DEPARTURE))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("http://localhost/api/plans/")))
                .andReturn().getResponse().getHeader(HttpHeaders.LOCATION);

        mvc.perform(get(location)).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(location.substring(location.lastIndexOf('/') + 1)));
    }

    @Test
    void defaultsAreTomorrowSixFromTheClockAndReturnToDepotFalseDropsTheReturnLeg() throws Exception {
        storeLegBothWays(depot, bstadt, 100, 90);
        Shipment shipment = shipment("PO-1", bstadt, 10, 12000, Priority.NORMAL, false, null);

        createPlan("""
                {"depotId": %d, "shipmentIds": [%d], "truckIds": [%d], "returnToDepot": false}
                """.formatted(depot.getId(), shipment.getId(), truck.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.request.returnToDepot").value(false))
                .andExpect(jsonPath("$.request.departureTime").value("2026-11-13T06:00:00"))
                .andExpect(jsonPath("$.optimal.routes[0].stops[0].arrivalTime").value("2026-11-13T07:30:00"))
                .andExpect(jsonPath("$.optimal.routes[0].returnLeg").value(nullValue()))
                .andExpect(jsonPath("$.optimal.routes[0].returnTime").value(nullValue()))
                .andExpect(jsonPath("$.optimal.totals.distanceKm").value(100.0));
    }

    @Test
    void missedDeadlineIsFlaggedOnTheStopAndWarnedOnTheRoute() throws Exception {
        storeLegBothWays(depot, bstadt, 100, 90);
        Shipment late = shipment("PO-1", bstadt, 10, 12000, Priority.NORMAL, false,
                LocalDateTime.of(2026, 11, 13, 7, 0));
        Shipment onTime = shipment("PO-2", bstadt, 2, 500, Priority.NORMAL, false,
                LocalDateTime.of(2026, 11, 13, 9, 0));

        createPlan(request(depot.getId(), List.of(late.getId(), onTime.getId()), List.of(truck.getId()), true,
                DEPARTURE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.optimal.deadlinesMissed").value(1))
                .andExpect(jsonPath("$.priority.deadlinesMissed").value(1))
                .andExpect(jsonPath("$.optimal.routes[0].stops[0].arrivalTime").value("2026-11-13T07:30:00"))
                .andExpect(jsonPath("$.optimal.routes[0].stops[0].deadline").value("2026-11-13T07:00:00"))
                .andExpect(jsonPath("$.optimal.routes[0].stops[0].deadlineMissed").value(true))
                .andExpect(jsonPath("$.optimal.routes[0].warnings", hasSize(1)))
                .andExpect(jsonPath("$.optimal.routes[0].warnings[0].code").value("DEADLINE_MISSED"))
                .andExpect(jsonPath("$.optimal.routes[0].warnings[0].severity").value("WARNING"))
                .andExpect(jsonPath("$.optimal.routes[0].warnings[0].truckId").value(truck.getId()))
                .andExpect(jsonPath("$.optimal.routes[0].warnings[0].shipmentIds", contains(late.getId().intValue())))
                .andExpect(jsonPath("$.optimal.routes[0].warnings[0].message").value(
                        "WX 1001A arrives in Bstadt at 13 Nov, 07:30, after the deadline of PO-1 (13 Nov, 07:00)."))
                .andExpect(jsonPath("$.warnings", hasSize(0)));
    }

    /**
     * Two pins on one truck: the URGENT pin to Bstadt wins (packing order), the NORMAL pin to Cstadt is dropped.
     * No legs are stored, so every leg is an estimate.
     */
    @Test
    void pinConflictAndEstimatedDistancesAreReportedWithUserFacingText() throws Exception {
        Warehouse cstadt = warehouses.save(new Warehouse("C Hub", "Cstadt", 53.0, 19.0));
        Shipment kept = shipment("PO-1", bstadt, 5, 2000, Priority.URGENT, true, null);
        Shipment dropped = shipment("PO-2", cstadt, 5, 2000, Priority.NORMAL, true, null);

        createPlan(request(depot.getId(), List.of(dropped.getId(), kept.getId()), List.of(truck.getId()), true,
                DEPARTURE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.priority.routes[0].stops[0].city").value("Bstadt"))
                .andExpect(jsonPath("$.priority.routes[0].stops[0].pinned").value(true))
                .andExpect(jsonPath("$.priority.routes[0].stops[0].legSource").value("ESTIMATED"))
                .andExpect(jsonPath("$.warnings", hasSize(2)))
                .andExpect(jsonPath("$.warnings[0].code").value("PIN_CONFLICT"))
                .andExpect(jsonPath("$.warnings[0].severity").value("WARNING"))
                .andExpect(jsonPath("$.warnings[0].truckId").value(truck.getId()))
                .andExpect(jsonPath("$.warnings[0].shipmentIds", contains(dropped.getId().intValue())))
                .andExpect(jsonPath("$.warnings[0].message").value("Truck WX 1001A has more than one pinned stop: "
                        + "Bstadt stays first; the pin of PO-2 was dropped, so it is ordered by priority."))
                .andExpect(jsonPath("$.warnings[1].code").value("ESTIMATED_DISTANCE"))
                .andExpect(jsonPath("$.warnings[1].severity").value("INFO"))
                .andExpect(jsonPath("$.warnings[1].truckId").value(nullValue()))
                .andExpect(jsonPath("$.warnings[1].shipmentIds", hasSize(0)))
                .andExpect(jsonPath("$.warnings[1].message",
                        startsWith("3 legs use estimated distances (straight line x road factor)")));
    }

    /**
     * Twin Hub has the same coordinates as B Hub and no stored leg to it: the B Hub - Twin Hub leg is a 0 km / 0 min
     * estimate. The second stop costs only its 30 min service (22.50 PLN) and arrives when the first one is done.
     */
    @Test
    void coLocatedWarehousesGiveAZeroLengthEstimatedLeg() throws Exception {
        Warehouse twin = warehouses.save(new Warehouse("Twin Hub", "Bstadt", 51.0, 20.0));
        storeLegBothWays(depot, bstadt, 100, 90);
        storeLegBothWays(depot, twin, 100, 90);
        Shipment toB = shipment("PO-1", bstadt, 10, 6000, Priority.NORMAL, false, null);
        Shipment toTwin = shipment("PO-2", twin, 10, 6000, Priority.NORMAL, false, null);

        createPlan(request(depot.getId(), List.of(toB.getId(), toTwin.getId()), List.of(truck.getId()), true,
                DEPARTURE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.optimal.routes[0].stops", hasSize(2)))
                .andExpect(jsonPath("$.optimal.routes[0].stops[1].legDistanceKm").value(0.0))
                .andExpect(jsonPath("$.optimal.routes[0].stops[1].legDurationMinutes").value(0))
                .andExpect(jsonPath("$.optimal.routes[0].stops[1].legLitres").value(0.0))
                .andExpect(jsonPath("$.optimal.routes[0].stops[1].legCostPln").value(22.5))
                .andExpect(jsonPath("$.optimal.routes[0].stops[1].legSource").value("ESTIMATED"))
                .andExpect(jsonPath("$.optimal.routes[0].stops[1].arrivalTime").value("2026-11-13T08:00:00"))
                .andExpect(jsonPath("$.optimal.totals.distanceKm").value(200.0))
                .andExpect(jsonPath("$.delta.noExtraCost").value(true))
                .andExpect(jsonPath("$.warnings", hasSize(1)))
                .andExpect(jsonPath("$.warnings[0].code").value("ESTIMATED_DISTANCE"))
                .andExpect(jsonPath("$.warnings[0].severity").value("INFO"))
                .andExpect(jsonPath("$.warnings[0].message",
                        startsWith("1 leg uses an estimated distance (straight line x road factor)")));
    }

    @Test
    void shipmentTooBigForEveryTruckIsUnassignedWithItsReference() throws Exception {
        storeLegBothWays(depot, bstadt, 100, 90);
        Shipment fits = shipment("PO-1", bstadt, 10, 5000, Priority.NORMAL, false, null);
        Shipment tooBig = shipment("PO-99", bstadt, 40, 5000, Priority.NORMAL, false, null);

        createPlan(request(depot.getId(), List.of(fits.getId(), tooBig.getId()), List.of(truck.getId()), true,
                DEPARTURE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.optimal.routes[0].stops[0].shipmentIds", contains(fits.getId().intValue())))
                .andExpect(jsonPath("$.unassigned", hasSize(1)))
                .andExpect(jsonPath("$.unassigned[0].shipmentId").value(tooBig.getId()))
                .andExpect(jsonPath("$.unassigned[0].reference").value("PO-99"))
                .andExpect(jsonPath("$.unassigned[0].reason").value("EXCEEDS_LARGEST_TRUCK"))
                .andExpect(jsonPath("$.unassigned[0].message")
                        .value("40 pallets exceed the largest selected truck (33 pallets)."));
    }

    @Test
    void nothingLoadedSaysThereIsNothingToCompare() throws Exception {
        Truck small = trucks.save(new Truck("WX 4004D", 10, 6000, 18, 5, 40, depot));
        Shipment heavy = shipment("PO-1", bstadt, 8, 30000, Priority.NORMAL, false, null);

        createPlan(request(depot.getId(), List.of(heavy.getId()), List.of(small.getId()), true, DEPARTURE))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.optimal.routes", hasSize(0)))
                .andExpect(jsonPath("$.priority.routes", hasSize(0)))
                .andExpect(jsonPath("$.optimal.totals.costPln").value(0.0))
                .andExpect(jsonPath("$.delta.costPln").value(0.0))
                .andExpect(jsonPath("$.delta.noExtraCost").value(true))
                .andExpect(jsonPath("$.delta.sentence").value(
                        "No shipments could be loaded onto the selected trucks, so there is nothing to compare."))
                .andExpect(jsonPath("$.unassigned[0].reference").value("PO-1"))
                .andExpect(jsonPath("$.unassigned[0].reason").value("EXCEEDS_LARGEST_TRUCK"))
                .andExpect(jsonPath("$.unassigned[0].message")
                        .value("30000 kg exceed the largest payload of the selected trucks (6000 kg)."));
    }

    @Test
    void missingFieldsAndEmptyListsReturn400WithAllFieldErrors() throws Exception {
        createPlan("{\"shipmentIds\": []}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[0].field").value("depotId"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be null"))
                .andExpect(jsonPath("$.errors[1].field").value("shipmentIds"))
                .andExpect(jsonPath("$.errors[1].message").value("size must be between 1 and 200"))
                .andExpect(jsonPath("$.errors[2].field").value("truckIds"))
                .andExpect(jsonPath("$.errors[2].message").value("must not be null"));
    }

    @Test
    void unknownDepotShipmentAndTruckReturn400() throws Exception {
        createPlan(request(MISSING_ID, List.of(MISSING_ID), List.of(MISSING_ID, MISSING_ID - 1), true, DEPARTURE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[0].field").value("depotId"))
                .andExpect(jsonPath("$.errors[0].message").value("warehouse " + MISSING_ID + " does not exist"))
                .andExpect(jsonPath("$.errors[1].field").value("shipmentIds"))
                .andExpect(jsonPath("$.errors[1].message").value("shipment " + MISSING_ID + " does not exist"))
                .andExpect(jsonPath("$.errors[2].field").value("truckIds"))
                .andExpect(jsonPath("$.errors[2].message")
                        .value("trucks " + MISSING_ID + ", " + (MISSING_ID - 1) + " do not exist"));
    }

    @Test
    void duplicateAndNullIdsReturn400() throws Exception {
        Shipment shipment = shipment("PO-1", bstadt, 10, 5000, Priority.NORMAL, false, null);

        createPlan("""
                {"depotId": %d, "shipmentIds": [%d, %d], "truckIds": [%d, null]}
                """.formatted(depot.getId(), shipment.getId(), shipment.getId(), truck.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[0].field").value("shipmentIds"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("must not contain duplicate ids: " + shipment.getId()))
                .andExpect(jsonPath("$.errors[1].field").value("truckIds"))
                .andExpect(jsonPath("$.errors[1].message").value("must not contain null ids"));
    }

    @Test
    void shipmentFromAnotherDepotReturns400NamingIt() throws Exception {
        Shipment fromDepot = shipment("PO-1", bstadt, 10, 5000, Priority.NORMAL, false, null);
        Shipment fromElsewhere = shipments.save(new Shipment("PO-9", bstadt, depot, 4, 1000));

        createPlan(request(depot.getId(), List.of(fromDepot.getId(), fromElsewhere.getId()), List.of(truck.getId()),
                true, DEPARTURE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("shipmentIds"))
                .andExpect(jsonPath("$.errors[0].message")
                        .value("PO-9 (id " + fromElsewhere.getId() + ") does not start at depot Depot A"));
    }

    /** T-32 + T-33: a truck of another depot and a truck on route are both named, with the shipment error, in one 400. */
    @Test
    void trucksBasedElsewhereOrOnRouteReturn400NamingEachTogetherWithOtherErrors() throws Exception {
        Shipment fromDepot = shipment("PO-1", bstadt, 10, 5000, Priority.NORMAL, false, null);
        Shipment fromElsewhere = shipments.save(new Shipment("PO-9", bstadt, depot, 4, 1000));
        Truck foreign = trucks.save(new Truck("PO 6006F", 33, 24000, 28, 10, 45, bstadt));
        Truck onRoute = new Truck("WX 2002B", 33, 24000, 29, 10, 45, depot);
        onRoute.setAvailable(false);
        onRoute = trucks.save(onRoute);

        createPlan(request(depot.getId(), List.of(fromDepot.getId(), fromElsewhere.getId()),
                List.of(truck.getId(), foreign.getId(), onRoute.getId()), true, DEPARTURE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[0].field").value("shipmentIds"))
                .andExpect(jsonPath("$.errors[1].field").value("truckIds"))
                .andExpect(jsonPath("$.errors[1].message").value("Truck PO 6006F is based at B Hub, not at Depot A."))
                .andExpect(jsonPath("$.errors[2].field").value("truckIds"))
                .andExpect(jsonPath("$.errors[2].message").value("Truck WX 2002B is already on route."));
    }

    @Test
    void truckOfAnotherDepotIsRejectedEvenWhenItIsTheOnlySelection() throws Exception {
        storeLegBothWays(depot, bstadt, 100, 90);
        Shipment shipment = shipment("PO-1", bstadt, 10, 5000, Priority.NORMAL, false, null);
        Truck foreign = trucks.save(new Truck("PO 6006F", 33, 24000, 28, 10, 45, bstadt));

        createPlan(request(depot.getId(), List.of(shipment.getId()), List.of(foreign.getId()), true, DEPARTURE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].message").value("Truck PO 6006F is based at B Hub, not at Depot A."));
    }

    @Test
    void tooManyIdsReturn400() throws Exception {
        List<Long> ids = LongStream.rangeClosed(1, 201).boxed().toList();

        createPlan(request(depot.getId(), ids, List.of(truck.getId()), true, DEPARTURE))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("shipmentIds"))
                .andExpect(jsonPath("$.errors[0].message").value("size must be between 1 and 200"));
    }

    @Test
    void departureTimeWithOffsetReturns400Malformed() throws Exception {
        createPlan("""
                {"depotId": 1, "shipmentIds": [1], "truckIds": [1], "departureTime": "2026-11-13T06:00:00Z"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail", startsWith("Field 'departureTime' must be a local date-time")));
    }

    @Test
    void unknownPlanIdReturns404() throws Exception {
        String unknown = UUID.randomUUID().toString();

        mvc.perform(get("/api/plans/{id}", unknown))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Plan " + unknown + " not found."));
        mvc.perform(get("/api/plans/{id}", "not-a-uuid"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void createFromAllowedOriginCarriesCorsHeaders() throws Exception {
        storeLegBothWays(depot, bstadt, 100, 90);
        Shipment shipment = shipment("PO-1", bstadt, 10, 5000, Priority.NORMAL, false, null);

        mvc.perform(post("/api/plans").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request(depot.getId(), List.of(shipment.getId()), List.of(truck.getId()), true,
                                DEPARTURE)))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.LOCATION))
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("http://localhost/api/plans/")));
    }

    private ResultActions createPlan(String json) throws Exception {
        return mvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private void storeLegBothWays(Warehouse a, Warehouse b, double km, double minutes) {
        LocalDateTime updated = LocalDateTime.of(2026, 11, 12, 8, 0);
        routeLegs.save(new RouteLeg(a, b, km, minutes, LegSource.SEEDED, updated));
        routeLegs.save(new RouteLeg(b, a, km, minutes, LegSource.SEEDED, updated));
    }

    private Shipment shipment(String reference, Warehouse destination, int pallets, int weightKg, Priority priority,
                              boolean pinned, LocalDateTime deadline) {
        Shipment shipment = new Shipment(reference, depot, destination, pallets, weightKg);
        shipment.setPriority(priority);
        shipment.setPinnedFirst(pinned);
        shipment.setDeadline(deadline);
        return shipments.save(shipment);
    }

    private static String request(Long depotId, List<Long> shipmentIds, List<Long> truckIds, Boolean returnToDepot,
                                  LocalDateTime departure) {
        return """
                {"depotId": %d, "shipmentIds": %s, "truckIds": %s, "returnToDepot": %s, "departureTime": "%s"}
                """.formatted(depotId, ids(shipmentIds), ids(truckIds), returnToDepot, departure);
    }

    private static String ids(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(", ", "[", "]"));
    }
}
