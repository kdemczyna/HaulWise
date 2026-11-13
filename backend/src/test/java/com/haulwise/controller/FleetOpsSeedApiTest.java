package com.haulwise.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.in;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.haulwise.config.ClockConfig;
import com.haulwise.domain.Warehouse;
import com.haulwise.dto.ShipmentResponse;
import com.haulwise.dto.TruckResponse;
import com.haulwise.repository.WarehouseRepository;
import com.haulwise.service.ShipmentService;
import com.haulwise.service.TruckService;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * T-32 and T-33 on the LOADED seed (own in-memory database, fixed clock; the context is discarded afterwards
 * because dispatching deletes seed shipments): POZ West plans and recommendations only use POZ trucks, and sending
 * the WAW demo takes its three routed trucks off the WAW fleet and removes the ten routed shipments, leaving
 * PO-1099 (unassigned) and the POZ shipments untouched.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:fleet-ops-seed-test-${random.uuid};DB_CLOSE_DELAY=-1",
        "spring.h2.console.enabled=false",
        "haulwise.seed.enabled=true",
        "haulwise.ai.api-key="
})
@AutoConfigureMockMvc
@DirtiesContext
class FleetOpsSeedApiTest {

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
    private ShipmentService shipmentService;

    @Autowired
    private TruckService truckService;

    @Test
    void pozWestPlansAndRecommendsOnlyWithItsOwnTrucks() throws Exception {
        Warehouse poz = warehouse("POZ West");
        List<Long> pozShipments = shipmentIds(poz);
        List<Long> pozTrucks = truckIds(poz);
        assertThat(trucks(poz)).extracting(TruckResponse::plate).containsExactly("PO 6006F", "PO 7007G");

        mvc.perform(post("/api/trucks/recommend").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"depotId\": %d, \"shipmentIds\": %s}".formatted(poz.getId(), json(pozShipments))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unassigned", hasSize(0)))
                .andExpect(jsonPath("$.trucks[*].plate", everyItem(in(List.of("PO 6006F", "PO 7007G")))));
        mvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON)
                        .content(planRequest(poz, pozShipments, pozTrucks)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.unassigned", hasSize(0)));
        mvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON)
                        .content(planRequest(poz, pozShipments, truckIds(warehouse("WAW Central")).subList(2, 3))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].message")
                        .value("Truck WX 3003C is based at WAW Central, not at POZ West."));
    }

    @Test
    void sendingTheWawDemoTakesItsRoutedTrucksAndShipmentsOutOfTheNextPlan() throws Exception {
        Warehouse waw = warehouse("WAW Central");
        Warehouse poz = warehouse("POZ West");
        List<Long> wawShipments = shipmentIds(waw);
        List<Long> wawTrucks = truckIds(waw);
        String created = mvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON)
                        .content(planRequest(waw, wawShipments, wawTrucks)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.delta.costPln").value(292.97))
                .andReturn().getResponse().getContentAsString();
        String planId = objectMapper.readTree(created).get("id").asText();

        mvc.perform(post("/api/plans/{id}/dispatch", planId).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\": \"PRIORITY\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trucks[*].plate", contains("WX 1001A", "WX 2002B", "PO 5005E")))
                .andExpect(jsonPath("$.removedShipments", hasSize(10)));

        assertThat(shipmentService.search(null, waw.getId())).extracting(ShipmentResponse::reference)
                .containsExactly("PO-1099");
        assertThat(shipmentService.search(null, poz.getId())).hasSize(3);
        assertThat(trucks(waw)).filteredOn(TruckResponse::available).extracting(TruckResponse::plate)
                .containsExactly("WX 3003C", "WX 4004D");
        assertThat(trucks(poz)).allMatch(TruckResponse::available);

        mvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON)
                        .content(planRequest(waw, shipmentIds(waw), wawTrucks)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].message", contains("Truck PO 5005E is already on route.",
                        "Truck WX 1001A is already on route.", "Truck WX 2002B is already on route.")));
    }

    private Warehouse warehouse(String name) {
        return warehouses.findAll().stream().filter(w -> w.getName().equals(name)).findFirst().orElseThrow();
    }

    private List<Long> shipmentIds(Warehouse origin) {
        return shipmentService.search(null, origin.getId()).stream().map(ShipmentResponse::id).toList();
    }

    /** The trucks based at the depot, in id (seed) order. */
    private List<TruckResponse> trucks(Warehouse home) {
        return truckService.findAll().stream()
                .filter(truck -> truck.homeWarehouse().id().equals(home.getId()))
                .sorted((a, b) -> Long.compare(a.id(), b.id()))
                .toList();
    }

    private List<Long> truckIds(Warehouse home) {
        return trucks(home).stream().map(TruckResponse::id).toList();
    }

    private static String planRequest(Warehouse depot, List<Long> shipmentIds, List<Long> truckIds) {
        return "{\"depotId\": %d, \"shipmentIds\": %s, \"truckIds\": %s}"
                .formatted(depot.getId(), json(shipmentIds), json(truckIds));
    }

    private static String json(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(", ", "[", "]"));
    }
}
