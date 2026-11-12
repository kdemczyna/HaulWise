package com.haulwise.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.haulwise.config.ClockConfig;
import com.haulwise.domain.Truck;
import com.haulwise.domain.Warehouse;
import com.haulwise.dto.ShipmentResponse;
import com.haulwise.repository.TruckRepository;
import com.haulwise.repository.WarehouseRepository;
import com.haulwise.service.ShipmentService;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The demo story end to end on the LOADED seed (seeding on, fixed clock): planning every WAW shipment on every
 * truck with the default request (return to depot, departure tomorrow 06:00) shows a non-zero cost of priority,
 * the pinned Gdansk delivery PO-1042 (deadline tomorrow 12:00) missed by OPTIMAL (arrives 23:18) and met by
 * PRIORITY (10:51), and PO-1099 unassigned. Guards {@code seed/shipments.csv} against drift.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:plan-seed-test-${random.uuid};DB_CLOSE_DELAY=-1",
        "spring.h2.console.enabled=false",
        "haulwise.seed.enabled=true",
        "haulwise.ai.api-key="
})
@AutoConfigureMockMvc
class PlanSeedDemoApiTest {

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
    private TruckRepository trucks;

    @Autowired
    private ShipmentService shipmentService;

    @Test
    void wawDemoShowsTheCostOfPriorityAndTheMissedDeadline() throws Exception {
        Warehouse waw = warehouses.findAll().stream()
                .filter(warehouse -> warehouse.getName().equals("WAW Central"))
                .findFirst().orElseThrow();
        List<ShipmentResponse> wawShipments = shipmentService.search(null, waw.getId());
        long po1042 = idOf(wawShipments, "PO-1042");
        long po1099 = idOf(wawShipments, "PO-1099");
        String body = """
                {"depotId": %d, "shipmentIds": %s, "truckIds": %s}
                """.formatted(waw.getId(), json(wawShipments.stream().map(ShipmentResponse::id).toList()),
                json(trucks.findAll().stream().map(Truck::getId).toList()));

        String response = mvc.perform(post("/api/plans").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.request.returnToDepot").value(true))
                .andExpect(jsonPath("$.request.departureTime").value("2026-11-13T06:00:00"))
                .andExpect(jsonPath("$.delta.costPln").value(292.97))
                .andExpect(jsonPath("$.delta.distanceKm").value(85.0))
                .andExpect(jsonPath("$.delta.durationMinutes").value(75))
                .andExpect(jsonPath("$.delta.noExtraCost").value(false))
                .andExpect(jsonPath("$.delta.sentence")
                        .value("Delivering Gdansk first costs 292.97 PLN more (85 km and 1 h 15 min longer)."))
                .andExpect(jsonPath("$.optimal.routes", hasSize(3)))
                .andExpect(jsonPath("$.optimal.routes[*].plate").value(contains("WX 1001A", "WX 2002B", "PO 5005E")))
                .andExpect(jsonPath("$.priority.routes[*].plate").value(contains("WX 1001A", "WX 2002B", "PO 5005E")))
                .andExpect(jsonPath("$.optimal.routes[*].colorIndex").value(contains(0, 1, 2)))
                .andExpect(jsonPath("$.priority.routes[*].colorIndex").value(contains(0, 1, 2)))
                .andExpect(jsonPath("$.optimal.routes[0].stops[*].city")
                        .value(contains("Lublin", "Krakow", "Katowice", "Gdansk")))
                .andExpect(jsonPath("$.priority.routes[0].stops[*].city")
                        .value(contains("Gdansk", "Lublin", "Krakow", "Katowice")))
                .andExpect(jsonPath("$.optimal.deadlinesMissed").value(1))
                .andExpect(jsonPath("$.priority.deadlinesMissed").value(0))
                .andExpect(jsonPath("$.optimal.routes[0].stops[3].arrivalTime").value("2026-11-13T23:18:00"))
                .andExpect(jsonPath("$.optimal.routes[0].stops[3].deadline").value("2026-11-13T12:00:00"))
                .andExpect(jsonPath("$.optimal.routes[0].stops[3].deadlineMissed").value(true))
                .andExpect(jsonPath("$.optimal.routes[0].stops[3].pinned").value(true))
                .andExpect(jsonPath("$.priority.routes[0].stops[0].sequence").value(1))
                .andExpect(jsonPath("$.priority.routes[0].stops[0].pinned").value(true))
                .andExpect(jsonPath("$.priority.routes[0].stops[0].arrivalTime").value("2026-11-13T10:51:00"))
                .andExpect(jsonPath("$.priority.routes[0].stops[0].deadlineMissed").value(false))
                .andExpect(jsonPath("$.optimal.routes[0].warnings[0].code").value("DEADLINE_MISSED"))
                .andExpect(jsonPath("$.optimal.routes[0].warnings[0].shipmentIds").value(contains((int) po1042)))
                .andExpect(jsonPath("$.optimal.routes[0].warnings[0].message").value("WX 1001A arrives in Gdansk "
                        + "at 2026-11-13 23:18, after the deadline of PO-1042 (2026-11-13 12:00)."))
                .andExpect(jsonPath("$.priority.routes[0].warnings", hasSize(0)))
                .andExpect(jsonPath("$.unassigned", hasSize(1)))
                .andExpect(jsonPath("$.unassigned[0].shipmentId").value(po1099))
                .andExpect(jsonPath("$.unassigned[0].reference").value("PO-1099"))
                .andExpect(jsonPath("$.unassigned[0].reason").value("EXCEEDS_LARGEST_TRUCK"))
                .andExpect(jsonPath("$.warnings", hasSize(0)))
                .andReturn().getResponse().getContentAsString();

        JsonNode plan = objectMapper.readTree(response);
        String id = plan.get("id").asText();
        mvc.perform(get("/api/plans/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.delta.costPln").value(292.97));
        assertThat(plan.at("/priority/totals/costPln").decimalValue())
                .isGreaterThan(plan.at("/optimal/totals/costPln").decimalValue());
    }

    private static long idOf(List<ShipmentResponse> shipments, String reference) {
        return shipments.stream().filter(s -> s.reference().equals(reference)).findFirst().orElseThrow().id();
    }

    private static String json(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(", ", "[", "]"));
    }
}
