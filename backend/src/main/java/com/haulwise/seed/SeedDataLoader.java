package com.haulwise.seed;

import com.haulwise.domain.Settings;
import com.haulwise.repository.RouteLegRepository;
import com.haulwise.repository.SettingsRepository;
import com.haulwise.repository.ShipmentRepository;
import com.haulwise.repository.TruckRepository;
import com.haulwise.repository.WarehouseRepository;
import com.haulwise.seed.SeedDataReader.SeedData;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Loads the demo data from {@code classpath:seed/*.csv} on startup when {@code haulwise.seed.enabled=true}
 * (ARCHITECTURE 4.1). Runs only on an empty database, in one transaction: invalid seed data throws a
 * {@link SeedDataException}, rolls everything back and stops the application.
 */
@Component
@ConditionalOnProperty(name = "haulwise.seed.enabled", havingValue = "true")
public class SeedDataLoader implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);

    private final Repositories repositories;
    private final SeedDataReader reader;
    private final Clock clock;
    private final TransactionTemplate transaction;

    @Autowired
    public SeedDataLoader(WarehouseRepository warehouses, TruckRepository trucks, ShipmentRepository shipments,
                          RouteLegRepository routeLegs, SettingsRepository settings, Validator validator,
                          Clock clock, PlatformTransactionManager transactionManager) {
        this(new Repositories(warehouses, trucks, shipments, routeLegs, settings), new SeedDataReader(validator),
                clock, transactionManager);
    }

    SeedDataLoader(Repositories repositories, SeedDataReader reader, Clock clock,
                   PlatformTransactionManager transactionManager) {
        this.repositories = repositories;
        this.reader = reader;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public void run(ApplicationArguments args) {
        seedIfEmpty();
    }

    /**
     * Seeds the database unless it already holds warehouses, trucks, shipments or route legs.
     *
     * @return whether data was loaded
     */
    public boolean seedIfEmpty() {
        return Boolean.TRUE.equals(transaction.execute(status -> {
            if (!repositories.isEmpty()) {
                log.info("Seed data skipped: the database already contains data.");
                return false;
            }
            save(reader.read(LocalDateTime.now(clock)));
            return true;
        }));
    }

    private void save(SeedData data) {
        repositories.warehouses().saveAll(data.warehouses());
        repositories.trucks().saveAll(data.trucks());
        repositories.shipments().saveAll(data.shipments());
        repositories.routeLegs().saveAll(data.legs());
        boolean settingsCreated = !repositories.settings().existsById(Settings.SINGLETON_ID);
        if (settingsCreated) {
            repositories.settings().save(Settings.withDefaults());
        }
        log.info("Seed data loaded: {} warehouses, {} trucks, {} shipments, {} route legs, settings {}.",
                data.warehouses().size(), data.trucks().size(), data.shipments().size(), data.legs().size(),
                settingsCreated ? "created with defaults" : "kept");
    }

    /** The repositories the loader writes to. */
    record Repositories(WarehouseRepository warehouses, TruckRepository trucks,
                        ShipmentRepository shipments, RouteLegRepository routeLegs,
                        SettingsRepository settings) {

        boolean isEmpty() {
            return warehouses.count() == 0 && trucks.count() == 0 && shipments.count() == 0
                    && routeLegs.count() == 0;
        }
    }
}
