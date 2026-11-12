package com.haulwise.seed;

import com.haulwise.domain.LegSource;
import com.haulwise.domain.Priority;
import com.haulwise.domain.RouteLeg;
import com.haulwise.domain.Shipment;
import com.haulwise.domain.Truck;
import com.haulwise.domain.Warehouse;
import com.haulwise.dto.ShipmentRequest;
import com.haulwise.dto.TruckRequest;
import com.haulwise.dto.WarehouseRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.springframework.core.io.ClassPathResource;

/**
 * Reads and validates the bundled seed CSV files into unsaved entities. Field values are checked with
 * the same Bean Validation rules as the REST API (the request records), plus uniqueness of warehouse
 * names and truck plates, warehouse name resolution and {@code origin != destination}. Any problem is a
 * {@link SeedDataException} naming the file and line.
 * <p>
 * Shipment deadlines are relative to "tomorrow" (the default departure day, D-10): {@code deadline_day}
 * 0 means tomorrow, 1 the day after, combined with {@code deadline_time} (HH:mm). Both empty = no deadline.
 */
public final class SeedDataReader {

    static final String DIRECTORY = "seed/";
    static final String WAREHOUSES = "warehouses.csv";
    static final String TRUCKS = "trucks.csv";
    static final String SHIPMENTS = "shipments.csv";
    static final String MATRIX = "matrix.csv";

    private static final List<String> WAREHOUSE_HEADER = List.of("name", "city", "lat", "lon");
    private static final List<String> TRUCK_HEADER = List.of("plate", "capacity_pallets", "payload_kg",
            "base_consumption_l_per_100km", "extra_consumption_full_load_l_per_100km", "driver_cost_pln_per_hour");
    private static final List<String> SHIPMENT_HEADER = List.of("reference", "origin", "destination", "pallets",
            "weight_kg", "priority", "pinned_first", "deadline_day", "deadline_time");

    private final Validator validator;
    private final SeedSource source;

    /** Reads the files bundled under {@code classpath:seed/}. */
    public SeedDataReader(Validator validator) {
        this(validator, SeedDataReader::openBundled);
    }

    SeedDataReader(Validator validator, SeedSource source) {
        this.validator = validator;
        this.source = source;
    }

    /**
     * @param now current local time; deadlines are anchored to the next day and legs are stamped with it
     */
    public SeedData read(LocalDateTime now) {
        Map<String, Warehouse> warehousesByName = readWarehouses();
        List<Truck> trucks = readTrucks();
        List<Shipment> shipments = readShipments(warehousesByName, now.toLocalDate().plusDays(1));
        List<RouteLeg> legs = readLegs(warehousesByName, now);
        return new SeedData(List.copyOf(warehousesByName.values()), trucks, shipments, legs);
    }

    private Map<String, Warehouse> readWarehouses() {
        Map<String, Warehouse> byName = new LinkedHashMap<>();
        for (SeedCsv.Row row : readCsv(WAREHOUSES, WAREHOUSE_HEADER)) {
            WarehouseRequest request = new WarehouseRequest(row.text("name"), row.text("city"),
                    row.decimal("lat"), row.decimal("lon"));
            requireValid(row, validator.validate(request));
            if (byName.putIfAbsent(key(request.name()), new Warehouse(request.name(), request.city(),
                    request.lat(), request.lon())) != null) {
                throw row.error("duplicate warehouse name '" + request.name() + "'");
            }
        }
        return byName;
    }

    private List<Truck> readTrucks() {
        List<Truck> trucks = new ArrayList<>();
        Set<String> plates = new HashSet<>();
        for (SeedCsv.Row row : readCsv(TRUCKS, TRUCK_HEADER)) {
            TruckRequest request = new TruckRequest(row.text("plate"), row.integer("capacity_pallets"),
                    row.integer("payload_kg"), row.decimal("base_consumption_l_per_100km"),
                    row.decimal("extra_consumption_full_load_l_per_100km"), row.decimal("driver_cost_pln_per_hour"));
            requireValid(row, validator.validate(request));
            if (!plates.add(request.plate())) {
                throw row.error("duplicate truck plate '" + request.plate() + "'");
            }
            trucks.add(new Truck(request.plate(), request.capacityPallets(), request.payloadKg(),
                    request.baseConsumptionLPer100Km(), request.extraConsumptionFullLoadLPer100Km(),
                    request.driverCostPlnPerHour()));
        }
        return List.copyOf(trucks);
    }

    private List<Shipment> readShipments(Map<String, Warehouse> warehousesByName, LocalDate tomorrow) {
        List<Shipment> shipments = new ArrayList<>();
        for (SeedCsv.Row row : readCsv(SHIPMENTS, SHIPMENT_HEADER)) {
            Warehouse origin = resolve(warehousesByName, row.requiredText("origin"), row::error);
            Warehouse destination = resolve(warehousesByName, row.requiredText("destination"), row::error);
            if (origin == destination) {
                throw row.error("origin and destination must differ (" + origin.getName() + ")");
            }
            String reference = row.text("reference");
            int pallets = row.integer("pallets");
            int weightKg = row.integer("weight_kg");
            requireValid(row, validator.validateValue(ShipmentRequest.class, "reference", reference));
            requireValid(row, validator.validateValue(ShipmentRequest.class, "pallets", pallets));
            requireValid(row, validator.validateValue(ShipmentRequest.class, "weightKg", weightKg));

            Shipment shipment = new Shipment(reference, origin, destination, pallets, weightKg);
            shipment.setPriority(priority(row));
            shipment.setPinnedFirst(row.bool("pinned_first"));
            shipment.setDeadline(deadline(row, tomorrow));
            shipments.add(shipment);
        }
        return List.copyOf(shipments);
    }

    private List<RouteLeg> readLegs(Map<String, Warehouse> warehousesByName, LocalDateTime now) {
        List<MatrixCsvRow> rows;
        try (Reader reader = source.open(MATRIX)) {
            rows = MatrixCsvParser.parse(reader);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + DIRECTORY + MATRIX, e);
        }
        Function<String, SeedDataException> error = message ->
                new SeedDataException(MatrixCsvParser.RESOURCE + ": " + message);
        List<RouteLeg> legs = new ArrayList<>();
        for (MatrixCsvRow row : rows) {
            for (MatrixCsvRow directed : List.of(row, row.reversed())) {
                Warehouse from = resolve(warehousesByName, directed.fromWarehouse(), error);
                Warehouse to = resolve(warehousesByName, directed.toWarehouse(), error);
                legs.add(new RouteLeg(from, to, directed.distanceKm(), directed.durationMinutes(),
                        LegSource.SEEDED, now));
            }
        }
        return List.copyOf(legs);
    }

    private static Priority priority(SeedCsv.Row row) {
        String value = row.requiredText("priority");
        try {
            return Priority.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw row.error("priority must be URGENT, HIGH or NORMAL, was '" + value + "'");
        }
    }

    private static LocalDateTime deadline(SeedCsv.Row row, LocalDate tomorrow) {
        boolean noDay = row.text("deadline_day").isEmpty();
        boolean noTime = row.text("deadline_time").isEmpty();
        if (noDay && noTime) {
            return null;
        }
        if (noDay || noTime) {
            throw row.error("deadline_day and deadline_time must both be set or both be empty");
        }
        int day = row.integer("deadline_day");
        if (day < 0) {
            throw row.error("deadline_day must be 0 (tomorrow) or later, was " + day);
        }
        try {
            return tomorrow.plusDays(day).atTime(LocalTime.parse(row.text("deadline_time")));
        } catch (DateTimeParseException e) {
            throw row.error("deadline_time must be HH:mm, was '" + row.text("deadline_time") + "'");
        }
    }

    private static Warehouse resolve(Map<String, Warehouse> warehousesByName, String name,
                                     Function<String, SeedDataException> error) {
        Warehouse warehouse = warehousesByName.get(key(name));
        if (warehouse == null) {
            throw error.apply("unknown warehouse '" + name + "' (not in " + WAREHOUSES + ")");
        }
        return warehouse;
    }

    private static <T> void requireValid(SeedCsv.Row row, Set<ConstraintViolation<T>> violations) {
        if (!violations.isEmpty()) {
            throw row.error(violations.stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .sorted()
                    .reduce((a, b) -> a + "; " + b)
                    .orElseThrow());
        }
    }

    private List<SeedCsv.Row> readCsv(String file, List<String> header) {
        try (Reader reader = source.open(file)) {
            return SeedCsv.parse(DIRECTORY + file, reader, header);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + DIRECTORY + file, e);
        }
    }

    private static Reader openBundled(String file) throws IOException {
        try {
            return new InputStreamReader(new ClassPathResource(DIRECTORY + file).getInputStream(),
                    StandardCharsets.UTF_8);
        } catch (FileNotFoundException e) {
            throw new SeedDataException(DIRECTORY + file + ": not found on the classpath");
        }
    }

    private static String key(String warehouseName) {
        return warehouseName.toLowerCase(Locale.ROOT);
    }

    /** Opens one seed file by name (e.g. {@code trucks.csv}); the reader closes what it opens. */
    @FunctionalInterface
    interface SeedSource {
        Reader open(String file) throws IOException;
    }

    /** Unsaved entities read from the seed files; shipments and legs reference the warehouse instances. */
    public record SeedData(List<Warehouse> warehouses, List<Truck> trucks, List<Shipment> shipments,
                           List<RouteLeg> legs) {
    }
}
