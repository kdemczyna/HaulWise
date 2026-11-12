package com.haulwise.controller;

import static com.haulwise.controller.ApiTestIds.MISSING_ID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haulwise.HaulWiseApiTest;
import com.haulwise.domain.LegSource;
import com.haulwise.domain.RouteLeg;
import com.haulwise.domain.Shipment;
import com.haulwise.domain.Warehouse;
import com.haulwise.repository.RouteLegRepository;
import com.haulwise.repository.ShipmentRepository;
import com.haulwise.repository.TruckRepository;
import com.haulwise.repository.WarehouseRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@HaulWiseApiTest
@AutoConfigureMockMvc
class WarehouseApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private WarehouseRepository warehouses;

    @Autowired
    private ShipmentRepository shipments;

    @Autowired
    private TruckRepository trucks;

    @Autowired
    private RouteLegRepository routeLegs;

    @BeforeEach
    void cleanDatabase() {
        routeLegs.deleteAll();
        shipments.deleteAll();
        trucks.deleteAll();
        warehouses.deleteAll();
    }

    @Test
    void createReturns201WithLocationAndBody() throws Exception {
        mvc.perform(post("/api/warehouses").contentType(MediaType.APPLICATION_JSON)
                        .content(json("  WAW Central ", "Warsaw", 52.2297, 21.0122)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/warehouses/" + onlyWarehouseId())))
                .andExpect(jsonPath("$.id").value(onlyWarehouseId()))
                .andExpect(jsonPath("$.name").value("WAW Central"))
                .andExpect(jsonPath("$.city").value("Warsaw"))
                .andExpect(jsonPath("$.lat").value(52.2297))
                .andExpect(jsonPath("$.lon").value(21.0122));
    }

    @Test
    void listIsSortedByNameIgnoringCase() throws Exception {
        save("WAW Central", "Warsaw");
        save("gdn Port", "Gdansk");
        save("KRK South", "Krakow");

        mvc.perform(get("/api/warehouses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].name").value("gdn Port"))
                .andExpect(jsonPath("$[1].name").value("KRK South"))
                .andExpect(jsonPath("$[2].name").value("WAW Central"));
    }

    @Test
    void getReturnsWarehouse() throws Exception {
        Warehouse waw = save("WAW Central", "Warsaw");

        mvc.perform(get("/api/warehouses/{id}", waw.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("WAW Central"));
    }

    @Test
    void getUnknownIdReturns404Problem() throws Exception {
        mvc.perform(get("/api/warehouses/{id}", MISSING_ID))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Warehouse " + MISSING_ID + " not found."))
                .andExpect(jsonPath("$.instance").value("/api/warehouses/" + MISSING_ID))
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void invalidBodyReturns400WithFieldErrors() throws Exception {
        mvc.perform(post("/api/warehouses").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \" \", \"city\": \"Warsaw\", \"lat\": 91, \"lon\": null}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.instance").value("/api/warehouses"))
                .andExpect(jsonPath("$.errors[?(@.field == 'lat')].message")
                        .value("must be less than or equal to 90.0"))
                .andExpect(jsonPath("$.errors[?(@.field == 'lon')].message").value("must not be null"))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists());
        assertThat(warehouses.count()).isZero();
    }

    @Test
    void nameTooShortAfterStrippingReturns400() throws Exception {
        mvc.perform(post("/api/warehouses").contentType(MediaType.APPLICATION_JSON)
                        .content(json(" a ", "Warsaw", 52.0, 21.0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").value("size must be between 2 and 60"));
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        mvc.perform(post("/api/warehouses").contentType(MediaType.APPLICATION_JSON).content("{\"name\": "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void nonNumericIdReturns400() throws Exception {
        mvc.perform(get("/api/warehouses/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void unsupportedMethodReturnsProblemWithCode() throws Exception {
        mvc.perform(delete("/api/warehouses"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void duplicateNameIgnoringCaseReturns409() throws Exception {
        save("WAW Central", "Warsaw");

        mvc.perform(post("/api/warehouses").contentType(MediaType.APPLICATION_JSON)
                        .content(json("waw central", "Warsaw", 52.0, 21.0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.detail").value("A warehouse named 'waw central' already exists."));
    }

    @Test
    void innerWhitespaceIsCollapsedSoSpacedDuplicateReturns409() throws Exception {
        mvc.perform(post("/api/warehouses").contentType(MediaType.APPLICATION_JSON)
                        .content(json("GDN \\t Port", "  Gdansk   Old  Town ", 54.35, 18.65)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("GDN Port"))
                .andExpect(jsonPath("$.city").value("Gdansk Old Town"));

        mvc.perform(post("/api/warehouses").contentType(MediaType.APPLICATION_JSON)
                        .content(json("  GDN   Port ", "Gdansk", 54.0, 18.0)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("A warehouse named 'GDN Port' already exists."));
        assertThat(warehouses.count()).isEqualTo(1);
    }

    @Test
    void noBreakSpacesAreCollapsedAndStripped() throws Exception {
        mvc.perform(post("/api/warehouses").contentType(MediaType.APPLICATION_JSON)
                        .content(json("\\u00A0GDN\\u00A0\\u00A0Port\\u00A0", "Gdansk", 54.35, 18.65)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("GDN Port"));
    }

    @Test
    void updateReturnsUpdatedWarehouse() throws Exception {
        Warehouse waw = save("WAW Central", "Warsaw");

        mvc.perform(put("/api/warehouses/{id}", waw.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("WAW Main", "Warszawa", 52.2297, 21.0122)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(waw.getId()))
                .andExpect(jsonPath("$.name").value("WAW Main"))
                .andExpect(jsonPath("$.city").value("Warszawa"));
    }

    @Test
    void updateToOwnNameWithDifferentCaseIsAllowed() throws Exception {
        Warehouse waw = save("WAW Central", "Warsaw");

        mvc.perform(put("/api/warehouses/{id}", waw.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("WAW CENTRAL", "Warsaw", 52.2297, 21.0122)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("WAW CENTRAL"));
    }

    @Test
    void updateToNameOfAnotherWarehouseReturns409() throws Exception {
        save("WAW Central", "Warsaw");
        Warehouse krk = save("KRK South", "Krakow");

        mvc.perform(put("/api/warehouses/{id}", krk.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("WAW Central", "Krakow", 50.0, 19.9)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void updateUnknownIdReturns404() throws Exception {
        mvc.perform(put("/api/warehouses/{id}", MISSING_ID).contentType(MediaType.APPLICATION_JSON)
                        .content(json("WAW Central", "Warsaw", 52.0, 21.0)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void updatingCoordinatesReturnsNewValuesAndRemovesStoredLegs() throws Exception {
        Warehouse waw = save("WAW Central", "Warsaw");
        Warehouse krk = save("KRK South", "Krakow");
        Warehouse gdn = save("GDN Port", "Gdansk");
        saveLegsBetween(waw, krk);
        saveLegsBetween(krk, gdn);

        mvc.perform(put("/api/warehouses/{id}", waw.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("WAW Central", "Warsaw", 52.1, 20.9)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/warehouses/{id}", waw.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lat").value(52.1))
                .andExpect(jsonPath("$.lon").value(20.9));
        assertThat(routeLegs.findByFromWarehouseIdAndToWarehouseId(waw.getId(), krk.getId())).isEmpty();
        assertThat(routeLegs.findByFromWarehouseIdAndToWarehouseId(krk.getId(), waw.getId())).isEmpty();
        assertThat(routeLegs.count()).as("legs not touching the moved warehouse stay").isEqualTo(2);
    }

    @Test
    void updatingOnlyNameKeepsStoredLegs() throws Exception {
        Warehouse waw = save("WAW Central", "Warsaw");
        Warehouse krk = save("KRK South", "Krakow");
        saveLegsBetween(waw, krk);

        mvc.perform(put("/api/warehouses/{id}", waw.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("WAW Main", "Warsaw", waw.getLat(), waw.getLon())))
                .andExpect(status().isOk());

        assertThat(routeLegs.count()).isEqualTo(2);
    }

    @Test
    void deleteReturns204AndRemovesLegs() throws Exception {
        Warehouse waw = save("WAW Central", "Warsaw");
        Warehouse krk = save("KRK South", "Krakow");
        saveLegsBetween(waw, krk);

        mvc.perform(delete("/api/warehouses/{id}", waw.getId()))
                .andExpect(status().isNoContent());

        assertThat(warehouses.existsById(waw.getId())).isFalse();
        assertThat(routeLegs.count()).isZero();
    }

    @Test
    void deleteReferencedWarehouseReturns409AndKeepsData() throws Exception {
        Warehouse waw = save("WAW Central", "Warsaw");
        Warehouse gdn = save("GDN Port", "Gdansk");
        saveLegsBetween(waw, gdn);
        shipments.save(new Shipment("PO-1", waw, gdn, 2, 800));
        shipments.save(new Shipment("PO-2", waw, gdn, 3, 900));

        mvc.perform(delete("/api/warehouses/{id}", gdn.getId()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.detail").value("Warehouse 'GDN Port' is used by 2 shipments."));

        assertThat(warehouses.existsById(gdn.getId())).isTrue();
        assertThat(routeLegs.count()).isEqualTo(2);
    }

    @Test
    void deleteUnknownIdReturns404() throws Exception {
        mvc.perform(delete("/api/warehouses/{id}", MISSING_ID))
                .andExpect(status().isNotFound());
    }

    private Warehouse save(String name, String city) {
        return warehouses.save(new Warehouse(name, city, 52.0, 19.0 + warehouses.count()));
    }

    private void saveLegsBetween(Warehouse a, Warehouse b) {
        LocalDateTime now = LocalDateTime.of(2026, 11, 12, 8, 0);
        routeLegs.save(new RouteLeg(a, b, 100.0, 90.0, LegSource.SEEDED, now));
        routeLegs.save(new RouteLeg(b, a, 100.0, 90.0, LegSource.SEEDED, now));
    }

    private Long onlyWarehouseId() {
        return warehouses.findAll().get(0).getId();
    }

    private static String json(String name, String city, double lat, double lon) {
        return """
                {"name": "%s", "city": "%s", "lat": %s, "lon": %s}
                """.formatted(name, city, lat, lon);
    }
}
