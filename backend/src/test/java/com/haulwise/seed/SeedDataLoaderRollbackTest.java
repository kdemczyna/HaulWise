package com.haulwise.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.haulwise.HaulWiseApiTest;
import com.haulwise.domain.Settings;
import com.haulwise.repository.RouteLegRepository;
import com.haulwise.repository.SettingsRepository;
import com.haulwise.repository.ShipmentRepository;
import com.haulwise.repository.TruckRepository;
import com.haulwise.repository.WarehouseRepository;
import com.haulwise.seed.SeedDataLoader.Repositories;
import jakarta.validation.Validator;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The seed runs in one transaction: invalid data or a failure half-way leaves the database empty. Uses the
 * shared test setup ({@code haulwise.seed.enabled=false}), so the context registers no loader of its own;
 * the cached context's database is emptied before each test.
 */
@HaulWiseApiTest
class SeedDataLoaderRollbackTest {

    @Autowired
    private ApplicationContext context;

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
    private Validator validator;

    @Autowired
    private Clock clock;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void emptyDatabase() {
        routeLegs.deleteAll();
        shipments.deleteAll();
        trucks.deleteAll();
        warehouses.deleteAll();
        settings.deleteAll();
    }

    @Test
    void disabledFlagRegistersNoLoader() {
        assertThat(context.getBeanNamesForType(SeedDataLoader.class)).isEmpty();
    }

    @Test
    void unknownWarehouseInMatrixFailsBeforeAnythingIsSaved() {
        SeedDataReader reader = new SeedDataReader(validator, file -> new StringReader(
                file.equals(SeedDataReader.MATRIX)
                        ? "from,to,distance_km,duration_minutes\nWAW Central,Nowhere,100,90\n"
                        : bundled(file)));
        SeedDataLoader loader = new SeedDataLoader(
                new Repositories(warehouses, trucks, shipments, routeLegs, settings), reader, clock,
                transactionManager);

        assertThatThrownBy(loader::seedIfEmpty)
                .isInstanceOf(SeedDataException.class)
                .hasMessageContaining("unknown warehouse 'Nowhere'");
        assertThat(warehouses.count()).isZero();
        assertThat(trucks.count()).isZero();
        assertThat(shipments.count()).isZero();
        assertThat(routeLegs.count()).isZero();
    }

    @Test
    void failureWhileSavingRollsBackTheWholeSeed() {
        SettingsRepository failingSettings = mock(SettingsRepository.class);
        when(failingSettings.existsById(Settings.SINGLETON_ID)).thenThrow(new IllegalStateException("boom"));
        SeedDataLoader loader = new SeedDataLoader(
                new Repositories(warehouses, trucks, shipments, routeLegs, failingSettings),
                new SeedDataReader(validator), clock, transactionManager);

        assertThatThrownBy(loader::seedIfEmpty).hasMessage("boom");
        assertThat(settings.count()).isZero();
        assertThat(warehouses.count()).isZero();
        assertThat(trucks.count()).isZero();
        assertThat(shipments.count()).isZero();
        assertThat(routeLegs.count()).isZero();
    }

    private static String bundled(String name) throws IOException {
        try (InputStream in = SeedDataLoaderRollbackTest.class.getResourceAsStream("/seed/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
