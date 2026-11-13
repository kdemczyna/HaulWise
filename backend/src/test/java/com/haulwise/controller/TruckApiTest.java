package com.haulwise.controller;

import static com.haulwise.controller.ApiTestIds.MISSING_ID;
import static org.assertj.core.api.Assertions.assertThat;
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
import com.haulwise.domain.Truck;
import com.haulwise.domain.Warehouse;
import com.haulwise.repository.RouteLegRepository;
import com.haulwise.repository.ShipmentRepository;
import com.haulwise.repository.TruckRepository;
import com.haulwise.repository.WarehouseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@HaulWiseApiTest
@AutoConfigureMockMvc
class TruckApiTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TruckRepository trucks;

    @Autowired
    private ShipmentRepository shipments;

    @Autowired
    private RouteLegRepository routeLegs;

    @Autowired
    private WarehouseRepository warehouses;

    private Warehouse home;

    @BeforeEach
    void cleanDatabase() {
        routeLegs.deleteAll();
        shipments.deleteAll();
        trucks.deleteAll();
        warehouses.deleteAll();
        home = warehouses.save(new Warehouse("WAW Central", "Warsaw", 52.2297, 21.0122));
    }

    @Test
    void createNormalisesPlateAndReturns201() throws Exception {
        mvc.perform(post("/api/trucks").contentType(MediaType.APPLICATION_JSON).content(json(" wx   1001a ", 33)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/trucks/" + trucks.findAll().get(0).getId())))
                .andExpect(jsonPath("$.plate").value("WX 1001A"))
                .andExpect(jsonPath("$.capacityPallets").value(33))
                .andExpect(jsonPath("$.payloadKg").value(24000))
                .andExpect(jsonPath("$.baseConsumptionLPer100Km").value(28.0))
                .andExpect(jsonPath("$.extraConsumptionFullLoadLPer100Km").value(10.0))
                .andExpect(jsonPath("$.driverCostPlnPerHour").value(45.0))
                .andExpect(jsonPath("$.homeWarehouse.id").value(home.getId()))
                .andExpect(jsonPath("$.homeWarehouse.name").value("WAW Central"))
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void listAndGetReturnTrucks() throws Exception {
        Truck truck = save("WX 2002B");
        save("PO 5005E");

        mvc.perform(get("/api/trucks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].plate").value("PO 5005E"))
                .andExpect(jsonPath("$[1].plate").value("WX 2002B"));
        mvc.perform(get("/api/trucks/{id}", truck.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plate").value("WX 2002B"));
    }

    @Test
    void getUnknownIdReturns404() throws Exception {
        mvc.perform(get("/api/trucks/{id}", MISSING_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Truck " + MISSING_ID + " not found."));
    }

    @Test
    void outOfRangeValuesReturn400WithFieldErrors() throws Exception {
        String body = """
                {"plate": "WX 1001A", "capacityPallets": 41, "payloadKg": null,
                 "baseConsumptionLPer100Km": 0, "extraConsumptionFullLoadLPer100Km": 10,
                 "driverCostPlnPerHour": 45, "homeWarehouseId": %d}
                """.formatted(home.getId());

        mvc.perform(post("/api/trucks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value("Request has 3 invalid fields."))
                .andExpect(jsonPath("$.errors", hasSize(3)))
                .andExpect(jsonPath("$.errors[0].field").value("baseConsumptionLPer100Km"))
                .andExpect(jsonPath("$.errors[1].field").value("capacityPallets"))
                .andExpect(jsonPath("$.errors[1].message").value("must be less than or equal to 40"))
                .andExpect(jsonPath("$.errors[2].field").value("payloadKg"));
    }

    @Test
    void plateTooShortAfterNormalisationReturns400() throws Exception {
        mvc.perform(post("/api/trucks").contentType(MediaType.APPLICATION_JSON).content(json("  a ", 10)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("plate"))
                .andExpect(jsonPath("$.errors[0].message").value("size must be between 2 and 15"));
    }

    @Test
    void duplicatePlateAfterNormalisationReturns409() throws Exception {
        save("WX 1001A");

        mvc.perform(post("/api/trucks").contentType(MediaType.APPLICATION_JSON).content(json("wx  1001a", 10)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.detail").value("A truck with plate 'WX 1001A' already exists."));
    }

    @Test
    void updateReturnsUpdatedTruck() throws Exception {
        Truck truck = save("WX 1001A");

        mvc.perform(put("/api/trucks/{id}", truck.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("wx 1001a", 18)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plate").value("WX 1001A"))
                .andExpect(jsonPath("$.capacityPallets").value(18));
    }

    @Test
    void updateToPlateOfAnotherTruckReturns409() throws Exception {
        save("WX 1001A");
        Truck other = save("WX 2002B");

        mvc.perform(put("/api/trucks/{id}", other.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("WX 1001A", 33)))
                .andExpect(status().isConflict());
    }

    @Test
    void updateUnknownIdReturns404() throws Exception {
        mvc.perform(put("/api/trucks/{id}", MISSING_ID).contentType(MediaType.APPLICATION_JSON).content(json("WX 1001A", 33)))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204ThenGetReturns404() throws Exception {
        Truck truck = save("WX 1001A");

        mvc.perform(delete("/api/trucks/{id}", truck.getId())).andExpect(status().isNoContent());
        mvc.perform(get("/api/trucks/{id}", truck.getId())).andExpect(status().isNotFound());
        assertThat(trucks.count()).isZero();
    }

    @Test
    void missingHomeDepotReturns400() throws Exception {
        String body = """
                {"plate": "WX 1001A", "capacityPallets": 33, "payloadKg": 24000, "baseConsumptionLPer100Km": 28.0,
                 "extraConsumptionFullLoadLPer100Km": 10.0, "driverCostPlnPerHour": 45.0}
                """;

        mvc.perform(post("/api/trucks").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", hasSize(1)))
                .andExpect(jsonPath("$.errors[0].field").value("homeWarehouseId"))
                .andExpect(jsonPath("$.errors[0].message").value("must not be null"));
    }

    @Test
    void unknownHomeDepotReturns400OnCreateAndUpdate() throws Exception {
        Truck truck = save("WX 2002B");

        mvc.perform(post("/api/trucks").contentType(MediaType.APPLICATION_JSON)
                        .content(json("WX 1001A", 33, MISSING_ID, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("homeWarehouseId"))
                .andExpect(jsonPath("$.errors[0].message").value("warehouse " + MISSING_ID + " does not exist"));
        mvc.perform(put("/api/trucks/{id}", truck.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("WX 2002B", 33, MISSING_ID, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("homeWarehouseId"));
        assertThat(trucks.count()).isEqualTo(1);
    }

    @Test
    void updateMovesTheTruckToAnotherHomeDepot() throws Exception {
        Truck truck = save("WX 1001A");
        Warehouse poz = warehouses.save(new Warehouse("POZ West", "Poznan", 52.4064, 16.9252));

        mvc.perform(put("/api/trucks/{id}", truck.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("WX 1001A", 33, poz.getId(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.homeWarehouse.id").value(poz.getId()))
                .andExpect(jsonPath("$.homeWarehouse.city").value("Poznan"));
    }

    @Test
    void availabilityIsToggledByTheFullUpdateAndKeptWhenOmitted() throws Exception {
        Truck truck = save("WX 1001A");

        mvc.perform(put("/api/trucks/{id}", truck.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("WX 1001A", 33, home.getId(), false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(false));
        mvc.perform(put("/api/trucks/{id}", truck.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("WX 1001A", 18)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacityPallets").value(18))
                .andExpect(jsonPath("$.available").value(false));
        mvc.perform(put("/api/trucks/{id}", truck.getId()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("WX 1001A", 18, home.getId(), true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true));
    }

    @Test
    void createCanStartAsOnRoute() throws Exception {
        mvc.perform(post("/api/trucks").contentType(MediaType.APPLICATION_JSON)
                        .content(json("WX 1001A", 33, home.getId(), false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.available").value(false));
    }

    @Test
    void deleteUnknownIdReturns404() throws Exception {
        mvc.perform(delete("/api/trucks/{id}", MISSING_ID)).andExpect(status().isNotFound());
    }

    private Truck save(String plate) {
        return trucks.save(new Truck(plate, 33, 24000, 28.0, 10.0, 45.0, home));
    }

    private String json(String plate, int capacityPallets) {
        return json(plate, capacityPallets, home.getId(), null);
    }

    private static String json(String plate, int capacityPallets, long homeWarehouseId, Boolean available) {
        return """
                {"plate": "%s", "capacityPallets": %d, "payloadKg": 24000, "baseConsumptionLPer100Km": 28.0,
                 "extraConsumptionFullLoadLPer100Km": 10.0, "driverCostPlnPerHour": 45.0,
                 "homeWarehouseId": %d, "available": %s}
                """.formatted(plate, capacityPallets, homeWarehouseId, available);
    }
}
