package com.haulwise.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import com.haulwise.dto.plan.PlanResponse;
import com.haulwise.dto.plan.PlanVariantResponse;
import com.haulwise.dto.plan.RouteResponse;
import com.haulwise.dto.plan.StopResponse;
import com.haulwise.repository.RouteLegRepository;
import com.haulwise.repository.SettingsRepository;
import com.haulwise.repository.ShipmentRepository;
import com.haulwise.repository.TruckRepository;
import com.haulwise.repository.WarehouseRepository;
import com.haulwise.service.PlanStore;
import com.haulwise.solver.model.Mode;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * {@code POST /api/plans/{id}/dispatch} (T-33). Fixture: depot A with trucks WX 1001A (33 pallets), WX 2002B (18) and
 * WX 3003C (10); PO-1 (20 pallets) and PO-2 (15) fit, PO-99 (40) fits no truck. Packing puts PO-1 on WX 1001A and
 * PO-2 on WX 2002B; WX 3003C is selected but gets no route.
 */
@HaulWiseApiTest
@AutoConfigureMockMvc
class PlanDispatchApiTest {

    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 11, 12, 9, 30, 0, 0, ClockConfig.ZONE);

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

    @Autowired
    private PlanStore planStore;

    private Warehouse depot;
    private Truck big;
    private Truck medium;
    private Truck unused;
    private Shipment po1;
    private Shipment po2;
    private Shipment tooBig;

    @BeforeEach
    void cleanDatabaseAndCreateFixture() {
        routeLegs.deleteAll();
        shipments.deleteAll();
        trucks.deleteAll();
        warehouses.deleteAll();
        settings.deleteAll();
        depot = warehouses.save(new Warehouse("Depot A", "Astadt", 52.0, 21.0));
        Warehouse bstadt = warehouses.save(new Warehouse("B Hub", "Bstadt", 51.0, 20.0));
        Warehouse cstadt = warehouses.save(new Warehouse("C Hub", "Cstadt", 51.5, 19.0));
        storeLegBothWays(depot, bstadt, 100, 90);
        storeLegBothWays(depot, cstadt, 150, 130);
        storeLegBothWays(bstadt, cstadt, 80, 70);
        big = trucks.save(new Truck("WX 1001A", 33, 24000, 28, 10, 45, depot));
        medium = trucks.save(new Truck("WX 2002B", 18, 12000, 22, 7, 42, depot));
        unused = trucks.save(new Truck("WX 3003C", 10, 6000, 18, 5, 40, depot));
        po1 = shipment("PO-1", bstadt, 20, 9000);
        po2 = shipment("PO-2", cstadt, 15, 7000);
        tooBig = shipment("PO-99", bstadt, 40, 9000);
    }

    @Test
    void dispatchMarksExactlyTheRoutedTrucksAndDeletesExactlyTheRoutedShipments() throws Exception {
        String planId = createPlan();

        dispatch(planId, "PRIORITY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.planId").value(planId))
                .andExpect(jsonPath("$.mode").value("PRIORITY"))
                .andExpect(jsonPath("$.dispatchedAt").value("2026-11-12T09:30:00"))
                .andExpect(jsonPath("$.trucks[*].plate", contains("WX 1001A", "WX 2002B")))
                .andExpect(jsonPath("$.removedShipments[*].reference", contains("PO-1", "PO-2")));

        assertThat(trucks.findById(big.getId()).orElseThrow().isAvailable()).isFalse();
        assertThat(trucks.findById(medium.getId()).orElseThrow().isAvailable()).isFalse();
        assertThat(trucks.findById(unused.getId()).orElseThrow().isAvailable()).isTrue();
        assertThat(shipments.findAll()).extracting(Shipment::getReference).containsExactly("PO-99");
        mvc.perform(get("/api/plans/{id}", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dispatchedAt").value("2026-11-12T09:30:00"));
    }

    @Test
    void secondDispatchReturns409AndChangesNothingFurther() throws Exception {
        String planId = createPlan();
        dispatch(planId, "OPTIMAL").andExpect(status().isOk());
        Truck backHome = trucks.findById(big.getId()).orElseThrow();
        backHome.setAvailable(true);
        trucks.save(backHome);

        dispatch(planId, "OPTIMAL")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.detail").value("This plan was already sent on 12 Nov 2026, 09:30."));
        dispatch(planId, "PRIORITY").andExpect(status().isConflict());

        assertThat(trucks.findById(big.getId()).orElseThrow().isAvailable()).isTrue();
        assertThat(trucks.findById(medium.getId()).orElseThrow().isAvailable()).isFalse();
        assertThat(shipments.findAll()).extracting(Shipment::getReference).containsExactly("PO-99");
    }

    @Test
    void dispatchSendsTheChosenVariantsRoutes() throws Exception {
        // Both planner variants share one truck assignment (D-03), so this stored plan is built by hand with
        // different routes per variant to prove the endpoint reads the variant named by "mode".
        UUID optimalId = storeHandBuiltPlan();
        UUID priorityId = storeHandBuiltPlan();

        dispatch(optimalId.toString(), "OPTIMAL")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trucks[*].plate", contains("WX 1001A")))
                .andExpect(jsonPath("$.removedShipments[*].reference", contains("PO-1")));
        assertThat(trucks.findById(big.getId()).orElseThrow().isAvailable()).isFalse();
        assertThat(trucks.findById(medium.getId()).orElseThrow().isAvailable()).isTrue();

        dispatch(priorityId.toString(), "PRIORITY")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trucks[*].plate", contains("WX 2002B")))
                .andExpect(jsonPath("$.removedShipments[*].reference", contains("PO-2")));
        assertThat(trucks.findById(medium.getId()).orElseThrow().isAvailable()).isFalse();
        assertThat(shipments.findAll()).extracting(Shipment::getReference).containsExactly("PO-99");
    }

    @Test
    void stalePlanOfAlreadySentShipmentsReturns409AndChangesNothing() throws Exception {
        String first = createPlan();
        String second = createPlan();
        dispatch(first, "OPTIMAL").andExpect(status().isOk());
        Truck backHome = trucks.findById(medium.getId()).orElseThrow();
        backHome.setAvailable(true);
        trucks.save(backHome);

        dispatch(second, "OPTIMAL")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This plan is out of date: truck WX 1001A is already on route "
                        + "and 2 of its shipments no longer exist. Plan the run again."));

        assertThat(trucks.findById(medium.getId()).orElseThrow().isAvailable()).isTrue();
        mvc.perform(get("/api/plans/{id}", second)).andExpect(jsonPath("$.dispatchedAt").doesNotExist());
    }

    @Test
    void aPlanWithADispatchedTruckIsRejectedAtCreation() throws Exception {
        dispatch(createPlan(), "OPTIMAL").andExpect(status().isOk());

        mvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON).content(planRequest(
                        List.of(tooBig.getId()), List.of(big.getId(), unused.getId()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("truckIds"))
                .andExpect(jsonPath("$.errors[0].message").value("Truck WX 1001A is already on route."));
    }

    @Test
    void planWithoutRoutesReturns409() throws Exception {
        String body = mvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON)
                        .content(planRequest(List.of(tooBig.getId()), List.of(big.getId()))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        dispatch(objectMapper.readTree(body).get("id").asText(), "OPTIMAL")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("This plan has no routes, so there are no trucks to send."));
        assertThat(trucks.findById(big.getId()).orElseThrow().isAvailable()).isTrue();
    }

    @Test
    void unknownPlanReturns404() throws Exception {
        String unknown = UUID.randomUUID().toString();

        dispatch(unknown, "OPTIMAL")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Plan " + unknown + " not found."));
        dispatch("not-a-uuid", "OPTIMAL").andExpect(status().isNotFound());
    }

    @Test
    void missingOrUnknownModeReturns400() throws Exception {
        String planId = createPlan();

        mvc.perform(post("/api/plans/{id}/dispatch", planId).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("mode"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be null"));
        dispatch(planId, "FASTEST")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        assertThat(shipments.count()).isEqualTo(3);
    }

    private String createPlan() throws Exception {
        String body = mvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON).content(planRequest(
                        List.of(po1.getId(), po2.getId(), tooBig.getId()),
                        List.of(big.getId(), medium.getId(), unused.getId()))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.optimal.routes[*].plate", contains("WX 1001A", "WX 2002B")))
                .andExpect(jsonPath("$.unassigned[0].reference").value("PO-99"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    /** OPTIMAL sends WX 1001A with PO-1, PRIORITY sends WX 2002B with PO-2. */
    private UUID storeHandBuiltPlan() {
        UUID id = UUID.randomUUID();
        PlanVariantResponse optimal = new PlanVariantResponse(Mode.OPTIMAL, null, 0,
                List.of(route(big, po1)), List.of());
        PlanVariantResponse priority = new PlanVariantResponse(Mode.PRIORITY, null, 0,
                List.of(route(medium, po2)), List.of());
        planStore.save(new PlanResponse(id, LocalDateTime.of(2026, 11, 12, 9, 0), null, null, optimal, priority,
                null, List.of(), List.of(), null));
        return id;
    }

    private static RouteResponse route(Truck truck, Shipment shipment) {
        StopResponse stop = new StopResponse(1, shipment.getDestination().getId(), "", "", 0, 0,
                List.of(shipment.getId()), shipment.getPallets(), shipment.getWeightKg(),
                Priority.NORMAL, false, null, 0, null, null, null, 0, null, null, null, false);
        return new RouteResponse(truck.getId(), truck.getPlate(), 0, null, null, null, List.of(stop), null, null,
                List.of());
    }

    private ResultActions dispatch(String planId, String mode) throws Exception {
        return mvc.perform(post("/api/plans/{id}/dispatch", planId).contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\": \"" + mode + "\"}"));
    }

    private String planRequest(List<Long> shipmentIds, List<Long> truckIds) {
        return """
                {"depotId": %d, "shipmentIds": %s, "truckIds": %s}
                """.formatted(depot.getId(), ids(shipmentIds), ids(truckIds));
    }

    private void storeLegBothWays(Warehouse a, Warehouse b, double km, double minutes) {
        LocalDateTime updated = LocalDateTime.of(2026, 11, 12, 8, 0);
        routeLegs.save(new RouteLeg(a, b, km, minutes, LegSource.SEEDED, updated));
        routeLegs.save(new RouteLeg(b, a, km, minutes, LegSource.SEEDED, updated));
    }

    private Shipment shipment(String reference, Warehouse destination, int pallets, int weightKg) {
        Shipment shipment = new Shipment(reference, depot, destination, pallets, weightKg);
        shipment.setPriority(Priority.NORMAL);
        return shipments.save(shipment);
    }

    private static String ids(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(", ", "[", "]"));
    }
}
