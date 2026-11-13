package com.haulwise.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.haulwise.config.ClockConfig;
import com.haulwise.domain.LegSource;
import com.haulwise.domain.Priority;
import com.haulwise.dto.ShipmentResponse;
import com.haulwise.repository.RouteLegRepository;
import com.haulwise.repository.SettingsRepository;
import com.haulwise.repository.ShipmentRepository;
import com.haulwise.repository.TruckRepository;
import com.haulwise.repository.WarehouseRepository;
import com.haulwise.service.ShipmentService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Starts the application with seeding enabled (unlike {@code @HaulWiseApiTest}) on its own in-memory
 * database and a fixed clock.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:seed-test-${random.uuid};DB_CLOSE_DELAY=-1",
        "spring.h2.console.enabled=false",
        "haulwise.seed.enabled=true",
        "haulwise.ai.api-key="
})
@AutoConfigureMockMvc
class SeedDataLoaderTest {

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
    private SeedDataLoader loader;

    @Autowired
    private WarehouseRepository warehouses;

    @Autowired
    private TruckRepository trucks;

    @Autowired
    private ShipmentRepository shipments;

    @Autowired
    private RouteLegRepository routeLegs;

    @Autowired
    private SettingsRepository settings;

    @Autowired
    private ShipmentService shipmentService;

    @Autowired
    private MockMvc mvc;

    @Test
    void startupSeedsTheDocumentedCounts() {
        assertThat(warehouses.count()).isEqualTo(9);
        assertThat(trucks.count()).isEqualTo(7);
        assertThat(shipments.count()).isEqualTo(14);
        assertThat(routeLegs.count()).isEqualTo(72);
        assertThat(settings.count()).isEqualTo(1);
        assertThat(routeLegs.findAll()).allSatisfy(leg -> assertThat(leg.getSource()).isEqualTo(LegSource.SEEDED));
    }

    @Test
    void seededShipmentsReferenceSeededWarehousesAndTellTheDemoStory() {
        List<ShipmentResponse> all = shipmentService.search(null, null);
        List<Long> warehouseIds = warehouses.findAll().stream().map(w -> w.getId()).toList();

        assertThat(all).hasSize(14).allSatisfy(shipment -> {
            assertThat(warehouseIds).contains(shipment.origin().id(), shipment.destination().id());
            assertThat(shipment.origin().id()).isNotEqualTo(shipment.destination().id());
        });
        assertThat(all).filteredOn(s -> s.priority() == Priority.URGENT)
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.destination().city()).isEqualTo("Lublin");
                    assertThat(s.deadline()).isEqualTo(LocalDateTime.of(2026, 11, 13, 20, 0));
                });
        assertThat(all).filteredOn(ShipmentResponse::pinnedFirst)
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.destination().city()).isEqualTo("Gdansk");
                    assertThat(s.priority()).isEqualTo(Priority.HIGH);
                    assertThat(s.reference()).isEqualTo("PO-1042");
                    assertThat(s.deadline()).isEqualTo(LocalDateTime.of(2026, 11, 13, 12, 0));
                });
        assertThat(all).filteredOn(s -> s.pallets() > 33).extracting(ShipmentResponse::reference)
                .containsExactly("PO-1099");
    }

    @Test
    void distanceMatrixIsFullySeeded() throws Exception {
        mvc.perform(get("/api/distances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warehouses", hasSize(9)))
                .andExpect(jsonPath("$.legs", hasSize(72)))
                .andExpect(jsonPath("$.legs[*].source", not(hasItem("ESTIMATED"))));
    }

    @Test
    void runningAgainDoesNotDuplicate() {
        boolean seeded = loader.seedIfEmpty();

        assertThat(seeded).isFalse();
        assertThat(warehouses.count()).isEqualTo(9);
        assertThat(trucks.count()).isEqualTo(7);
        assertThat(shipments.count()).isEqualTo(14);
        assertThat(routeLegs.count()).isEqualTo(72);
        assertThat(settings.count()).isEqualTo(1);
    }
}
