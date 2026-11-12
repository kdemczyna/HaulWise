package com.haulwise.controller;

import static com.haulwise.controller.ApiTestIds.MISSING_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haulwise.HaulWiseApiTest;
import com.haulwise.domain.Priority;
import com.haulwise.domain.Shipment;
import com.haulwise.domain.Warehouse;
import com.haulwise.repository.RouteLegRepository;
import com.haulwise.repository.ShipmentRepository;
import com.haulwise.repository.TruckRepository;
import com.haulwise.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@HaulWiseApiTest
@AutoConfigureMockMvc
class ShipmentApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ShipmentRepository shipments;

    @Autowired
    private WarehouseRepository warehouses;

    @Autowired
    private TruckRepository trucks;

    @Autowired
    private RouteLegRepository routeLegs;

    private Warehouse waw;
    private Warehouse gdn;
    private Warehouse poz;

    @BeforeEach
    void setUp() {
        routeLegs.deleteAll();
        shipments.deleteAll();
        trucks.deleteAll();
        warehouses.deleteAll();
        waw = warehouses.save(new Warehouse("WAW Central", "Warsaw", 52.2297, 21.0122));
        gdn = warehouses.save(new Warehouse("GDN Port", "Gdansk", 54.3520, 18.6466));
        poz = warehouses.save(new Warehouse("POZ West", "Poznan", 52.4064, 16.9252));
    }

    @Test
    void createReturns201WithEmbeddedWarehousesAndDefaults() throws Exception {
        String body = """
                {"reference": "PO-1042", "originId": %d, "destinationId": %d, "pallets": 10, "weightKg": 6500}
                """.formatted(waw.getId(), gdn.getId());

        mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        endsWith("/api/shipments/" + shipments.findAll().get(0).getId())))
                .andExpect(jsonPath("$.reference").value("PO-1042"))
                .andExpect(jsonPath("$.origin.id").value(waw.getId()))
                .andExpect(jsonPath("$.origin.name").value("WAW Central"))
                .andExpect(jsonPath("$.origin.city").value("Warsaw"))
                .andExpect(jsonPath("$.destination.name").value("GDN Port"))
                .andExpect(jsonPath("$.pallets").value(10))
                .andExpect(jsonPath("$.weightKg").value(6500))
                .andExpect(jsonPath("$.priority").value("NORMAL"))
                .andExpect(jsonPath("$.deadline").isEmpty())
                .andExpect(jsonPath("$.pinnedFirst").value(false));
    }

    @Test
    void createAcceptsAllFieldsAndAboveTruckCapacityValues() throws Exception {
        mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content(fullJson(waw.getId(), gdn.getId(), 100, "URGENT")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pallets").value(100))
                .andExpect(jsonPath("$.priority").value("URGENT"))
                .andExpect(jsonPath("$.deadline").value("2026-11-13T14:30:00"))
                .andExpect(jsonPath("$.pinnedFirst").value(true));
    }

    @Test
    void sameOriginAndDestinationReturns400OnDestinationId() throws Exception {
        mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content(fullJson(waw.getId(), waw.getId(), 5, "NORMAL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("destinationId"))
                .andExpect(jsonPath("$.errors[0].message").value("must differ from originId"));
        assertThat(shipments.count()).isZero();
    }

    @Test
    void unknownWarehouseReturns400OnThatField() throws Exception {
        mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content(fullJson(MISSING_ID, gdn.getId(), 5, "NORMAL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("originId"))
                .andExpect(jsonPath("$.errors[0].message").value("warehouse " + MISSING_ID + " does not exist"));
    }

    @Test
    void outOfRangeAndMissingFieldsReturn400() throws Exception {
        String body = """
                {"reference": "", "originId": %d, "pallets": 101, "weightKg": 0}
                """.formatted(waw.getId());

        mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field")
                        .value(containsInAnyOrder(
                                "destinationId", "pallets", "reference", "weightKg")));
    }

    @Test
    void rangeAndWarehouseErrorsAreReportedTogether() throws Exception {
        mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content(fullJson(MISSING_ID, gdn.getId(), 101, "NORMAL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Request has 2 invalid fields."))
                .andExpect(jsonPath("$.errors[*].field").value(contains("originId", "pallets")))
                .andExpect(jsonPath("$.errors[0].message").value("warehouse " + MISSING_ID + " does not exist"))
                .andExpect(jsonPath("$.errors[1].message").value("must be less than or equal to 100"));
        assertThat(shipments.count()).isZero();
    }

    @Test
    void updateReportsRangeAndSameWarehouseErrorsTogether() throws Exception {
        Shipment shipment = save("PO-1", waw, gdn, Priority.HIGH);

        mvc.perform(put("/api/shipments/{id}", shipment.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(fullJson(waw.getId(), waw.getId(), 0, "NORMAL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(contains("destinationId", "pallets")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUPER", "urgent", "Low"})
    void unknownPriorityInBodyReturns400ListingAllowedValues(String priority) throws Exception {
        mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content(fullJson(waw.getId(), gdn.getId(), 5, priority)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Field 'priority' must be one of URGENT, HIGH, NORMAL."));
    }

    @Test
    void numericPriorityInBodyReturns400ListingAllowedValues() throws Exception {
        String body = """
                {"reference": "PO-3002", "originId": %d, "destinationId": %d, "pallets": 2, "weightKg": 900,
                 "priority": 1}
                """.formatted(waw.getId(), gdn.getId());

        mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Field 'priority' must be one of URGENT, HIGH, NORMAL."));
        assertThat(shipments.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"2026-11-13T12:00:00Z", "2026-11-13T12:00:00+01:00",
            "2026-11-13T12:00:00+01:00[Europe/Warsaw]", "2026-11-13", "13.11.2026 12:00"})
    void deadlineWithOffsetZoneOrOtherFormatReturns400(String deadline) throws Exception {
        mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonWithDeadline("\"" + deadline + "\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Field 'deadline' must be a local date-time without offset, "
                        + "e.g. 2026-11-13T12:00:00."));
        assertThat(shipments.count()).isZero();
    }

    @Test
    void numericDeadlineReturns400() throws Exception {
        mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonWithDeadline("1794567600")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void localDeadlineWithoutSecondsIsAcceptedAndEmptyMeansNone() throws Exception {
        mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonWithDeadline("\"2026-11-13T12:00\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deadline").value("2026-11-13T12:00:00"));
        mvc.perform(post("/api/shipments").contentType(MediaType.APPLICATION_JSON)
                        .content(jsonWithDeadline("\"\"")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deadline").isEmpty());
    }

    @Test
    void listFiltersByPriorityAndOrigin() throws Exception {
        save("PO-1", waw, gdn, Priority.URGENT);
        save("PO-2", waw, poz, Priority.NORMAL);
        save("PO-3", poz, gdn, Priority.URGENT);

        mvc.perform(get("/api/shipments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
        mvc.perform(get("/api/shipments").param("priority", "URGENT").param("originId", waw.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].reference").value("PO-1"))
                .andExpect(jsonPath("$[0].destination.name").value("GDN Port"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SUPER", "urgent"})
    void unknownPriorityFilterReturns400ListingAllowedValues(String priority) throws Exception {
        mvc.perform(get("/api/shipments").param("priority", priority))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Parameter 'priority' must be one of URGENT, HIGH, NORMAL."));
    }

    @Test
    void getReturnsShipmentAndUnknownIdReturns404() throws Exception {
        Shipment shipment = save("PO-1", waw, gdn, Priority.HIGH);

        mvc.perform(get("/api/shipments/{id}", shipment.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origin.name").value("WAW Central"))
                .andExpect(jsonPath("$.priority").value("HIGH"));
        mvc.perform(get("/api/shipments/{id}", MISSING_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void updateReplacesAllFields() throws Exception {
        Shipment shipment = save("PO-1", waw, gdn, Priority.HIGH);

        mvc.perform(put("/api/shipments/{id}", shipment.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(fullJson(poz.getId(), waw.getId(), 7, "URGENT")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(shipment.getId()))
                .andExpect(jsonPath("$.origin.name").value("POZ West"))
                .andExpect(jsonPath("$.destination.name").value("WAW Central"))
                .andExpect(jsonPath("$.pallets").value(7))
                .andExpect(jsonPath("$.priority").value("URGENT"));
    }

    @Test
    void updateUnknownIdReturns404() throws Exception {
        mvc.perform(put("/api/shipments/{id}", MISSING_ID).contentType(MediaType.APPLICATION_JSON)
                        .content(fullJson(waw.getId(), gdn.getId(), 5, "NORMAL")))
                .andExpect(status().isNotFound());
    }

    @Test
    void updateUnknownIdWithInvalidBodyReturns400BeforeNotFound() throws Exception {
        mvc.perform(put("/api/shipments/{id}", MISSING_ID).contentType(MediaType.APPLICATION_JSON)
                        .content(fullJson(waw.getId(), gdn.getId(), 101, "NORMAL")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("pallets"));
    }

    @Test
    void deleteReturns204AndUnknownIdReturns404() throws Exception {
        Shipment shipment = save("PO-1", waw, gdn, Priority.NORMAL);

        mvc.perform(delete("/api/shipments/{id}", shipment.getId())).andExpect(status().isNoContent());
        mvc.perform(delete("/api/shipments/{id}", shipment.getId())).andExpect(status().isNotFound());
        assertThat(shipments.count()).isZero();
    }

    private Shipment save(String reference, Warehouse origin, Warehouse destination, Priority priority) {
        Shipment shipment = new Shipment(reference, origin, destination, 4, 1500);
        shipment.setPriority(priority);
        return shipments.save(shipment);
    }

    private String jsonWithDeadline(String deadlineJson) {
        return """
                {"reference": "PO-3001", "originId": %d, "destinationId": %d, "pallets": 2, "weightKg": 900,
                 "deadline": %s}
                """.formatted(waw.getId(), gdn.getId(), deadlineJson);
    }

    private static String fullJson(Long originId, Long destinationId, int pallets, String priority) {
        return """
                {"reference": "PO-2001", "originId": %d, "destinationId": %d, "pallets": %d, "weightKg": 9000,
                 "priority": "%s", "deadline": "2026-11-13T14:30:00", "pinnedFirst": true}
                """.formatted(originId, destinationId, pallets, priority);
    }
}
