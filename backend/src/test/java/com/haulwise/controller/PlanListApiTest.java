package com.haulwise.controller;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
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
import com.haulwise.repository.RouteLegRepository;
import com.haulwise.repository.SettingsRepository;
import com.haulwise.repository.ShipmentRepository;
import com.haulwise.repository.TruckRepository;
import com.haulwise.repository.WarehouseRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;

/**
 * {@code GET /api/plans} (T-34). The plan store is a singleton bean, so each test gets a fresh context (an empty
 * store). A clock advanced by hand gives every plan its own {@code createdAt}.
 */
@HaulWiseApiTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PlanListApiTest {

    private static final ZonedDateTime START = ZonedDateTime.of(2026, 11, 14, 8, 0, 0, 0, ClockConfig.ZONE);

    @TestConfiguration
    static class ManualClockConfig {

        @Bean
        @Primary
        ManualClock manualClock() {
            return new ManualClock(START.toInstant());
        }
    }

    /** A clock that only moves when told to. */
    static class ManualClock extends Clock {

        private Instant now;

        ManualClock(Instant start) {
            this.now = start;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ClockConfig.ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ManualClock clock;

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
    private Truck big;
    private Truck medium;
    private Shipment po1;
    private Shipment po2;

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
        po1 = shipment("PO-1", bstadt, 20, 9000);
        po2 = shipment("PO-2", cstadt, 15, 7000);
    }

    @Test
    void emptyStoreListsNoPlans() throws Exception {
        mvc.perform(get("/api/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void listsNewestCreatedFirstEvenAfterAnOlderPlanWasOpenedOrSent() throws Exception {
        String a = createPlan(List.of(po1.getId()), List.of(big.getId()));
        clock.advance(Duration.ofMinutes(5));
        String b = createPlan(List.of(po1.getId(), po2.getId()), List.of(big.getId(), medium.getId()));
        clock.advance(Duration.ofMinutes(5));
        String c = createPlan(List.of(po2.getId()), List.of(big.getId(), medium.getId()));
        clock.advance(Duration.ofMinutes(5));

        // Both touch A and B in the store's access (LRU) order; the list must not follow it.
        mvc.perform(get("/api/plans/{id}", a)).andExpect(status().isOk());
        mvc.perform(post("/api/plans/{id}/dispatch", b).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mode\": \"OPTIMAL\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/plans/{id}", a)).andExpect(status().isOk());

        mvc.perform(get("/api/plans"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains(c, b, a)))
                .andExpect(jsonPath("$[*].createdAt",
                        contains("2026-11-14T08:10:00", "2026-11-14T08:05:00", "2026-11-14T08:00:00")))
                .andExpect(jsonPath("$[*].dispatchedAt", contains(nullValue(), is("2026-11-14T08:15:00"),
                        nullValue())))
                .andExpect(jsonPath("$[*].shipmentCount", contains(1, 2, 1)))
                .andExpect(jsonPath("$[*].truckCount", contains(2, 2, 1)))
                .andExpect(jsonPath("$[0].depotId").value(depot.getId()))
                .andExpect(jsonPath("$[0].depotName").value("Depot A"))
                .andExpect(jsonPath("$[0].optimal").doesNotExist())
                .andExpect(jsonPath("$[0].request").doesNotExist());
    }

    private String createPlan(List<Long> shipmentIds, List<Long> truckIds) throws Exception {
        String body = mvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON).content("""
                        {"depotId": %d, "shipmentIds": %s, "truckIds": %s}
                        """.formatted(depot.getId(), ids(shipmentIds), ids(truckIds))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
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
