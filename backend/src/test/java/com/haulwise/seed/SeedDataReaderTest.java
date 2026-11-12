package com.haulwise.seed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import com.haulwise.config.ValidationConfig;
import com.haulwise.domain.LegSource;
import com.haulwise.domain.Priority;
import com.haulwise.domain.RouteLeg;
import com.haulwise.domain.Shipment;
import com.haulwise.domain.Truck;
import com.haulwise.domain.Warehouse;
import com.haulwise.seed.SeedDataReader.SeedData;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

class SeedDataReaderTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 9, 30);

    /** The application's validator (English messages), as the Spring context provides it. */
    private static LocalValidatorFactoryBean validator;

    @BeforeAll
    static void createValidator() {
        validator = ValidationConfig.defaultValidator();
        validator.afterPropertiesSet();
    }

    @AfterAll
    static void closeValidator() {
        validator.destroy();
    }

    @Test
    void bundledWarehousesMatchTheDocumentedSeed() {
        SeedData data = new SeedDataReader(validator).read(NOW);

        assertThat(data.warehouses())
                .extracting(Warehouse::getName, Warehouse::getCity, Warehouse::getLat, Warehouse::getLon)
                .containsExactly(
                        tuple("WAW Central", "Warsaw", 52.2297, 21.0122),
                        tuple("KRK South", "Krakow", 50.0647, 19.9450),
                        tuple("GDN Port", "Gdansk", 54.3520, 18.6466),
                        tuple("POZ West", "Poznan", 52.4064, 16.9252),
                        tuple("WRO Hub", "Wroclaw", 51.1079, 17.0385),
                        tuple("LDZ Cross-dock", "Lodz", 51.7592, 19.4560),
                        tuple("KTW Silesia", "Katowice", 50.2649, 19.0238),
                        tuple("LUB East", "Lublin", 51.2465, 22.5684),
                        tuple("SZZ North-West", "Szczecin", 53.4285, 14.5528));
    }

    @Test
    void bundledTrucksMatchTheDocumentedSeed() {
        SeedData data = new SeedDataReader(validator).read(NOW);

        assertThat(data.trucks())
                .extracting(Truck::getPlate, Truck::getCapacityPallets, Truck::getPayloadKg,
                        Truck::getBaseConsumptionLPer100Km, Truck::getExtraConsumptionFullLoadLPer100Km,
                        Truck::getDriverCostPlnPerHour)
                .containsExactly(
                        tuple("WX 1001A", 33, 24000, 28.0, 10.0, 45.0),
                        tuple("WX 2002B", 33, 24000, 29.0, 10.0, 45.0),
                        tuple("WX 3003C", 18, 12000, 22.0, 7.0, 42.0),
                        tuple("WX 4004D", 10, 6000, 18.0, 5.0, 40.0),
                        tuple("PO 5005E", 33, 24000, 27.0, 10.0, 45.0));
    }

    @Test
    void bundledShipmentsTellTheDemoStory() {
        SeedData data = new SeedDataReader(validator).read(NOW);
        Map<String, Shipment> byReference = new HashMap<>();
        data.shipments().forEach(shipment -> byReference.put(shipment.getReference(), shipment));

        assertThat(data.shipments()).hasSize(14);
        assertThat(byReference).hasSize(14);
        assertThat(data.shipments()).filteredOn(s -> s.getOrigin().getName().equals("WAW Central")).hasSize(11);
        assertThat(data.shipments()).filteredOn(s -> s.getOrigin().getName().equals("POZ West")).hasSize(3);

        Shipment urgent = byReference.get("PO-1041");
        assertThat(urgent.getDestination().getCity()).isEqualTo("Lublin");
        assertThat(urgent.getPriority()).isEqualTo(Priority.URGENT);
        assertThat(urgent.getDeadline()).isEqualTo(LocalDateTime.of(2026, 11, 13, 20, 0));

        Shipment pinned = byReference.get("PO-1042");
        assertThat(pinned.getDestination().getCity()).isEqualTo("Gdansk");
        assertThat(pinned.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(pinned.isPinnedFirst()).isTrue();
        assertThat(data.shipments()).filteredOn(Shipment::isPinnedFirst).containsExactly(pinned);

        int largestTruckPallets = data.trucks().stream().mapToInt(Truck::getCapacityPallets).max().orElseThrow();
        int largestPayload = data.trucks().stream().mapToInt(Truck::getPayloadKg).max().orElseThrow();
        assertThat(data.shipments())
                .filteredOn(s -> s.getPallets() > largestTruckPallets || s.getWeightKg() > largestPayload)
                .extracting(Shipment::getReference, Shipment::getPallets)
                .containsExactly(tuple("PO-1099", 36));
    }

    @Test
    void deadlinesAreRelativeToTomorrow() {
        SeedData data = new SeedDataReader(validator).read(LocalDateTime.of(2027, 2, 28, 23, 59));

        assertThat(data.shipments())
                .filteredOn(s -> s.getReference().equals("PO-1041"))
                .extracting(Shipment::getDeadline)
                .containsExactly(LocalDateTime.of(2027, 3, 1, 20, 0));
        assertThat(data.shipments())
                .filteredOn(s -> s.getReference().equals("PO-1043"))
                .extracting(Shipment::getDeadline)
                .containsExactly(LocalDateTime.of(2027, 3, 2, 12, 0));
    }

    @Test
    void bundledMatrixGivesBothDirectionsOfEveryPairAsSeededLegs() {
        SeedData data = new SeedDataReader(validator).read(NOW);

        assertThat(data.legs()).hasSize(72);
        assertThat(data.legs()).allSatisfy(leg -> {
            assertThat(leg.getSource()).isEqualTo(LegSource.SEEDED);
            assertThat(leg.getUpdatedAt()).isEqualTo(NOW);
            assertThat(data.warehouses()).contains(leg.getFromWarehouse(), leg.getToWarehouse());
        });
        Set<String> pairs = new HashSet<>();
        for (RouteLeg leg : data.legs()) {
            pairs.add(leg.getFromWarehouse().getName() + ">" + leg.getToWarehouse().getName());
        }
        assertThat(pairs).hasSize(72).contains("WAW Central>LUB East", "LUB East>WAW Central");
    }

    @Test
    void unknownWarehouseInMatrixFails() {
        SeedDataReader reader = readerWith(SeedDataReader.MATRIX, """
                from,to,distance_km,duration_minutes
                WAW Central,KRK South,295,253
                WAW Central,BER Gateway,575,500
                """);

        assertThatThrownBy(() -> reader.read(NOW))
                .isInstanceOf(SeedDataException.class)
                .hasMessageContaining("seed/matrix.csv")
                .hasMessageContaining("unknown warehouse 'BER Gateway'");
    }

    @Test
    void unknownWarehouseInShipmentsFailsWithLine() {
        SeedDataReader reader = readerWith(SeedDataReader.SHIPMENTS, """
                reference,origin,destination,pallets,weight_kg,priority,pinned_first,deadline_day,deadline_time
                PO-1,WAW Central,KRK South,2,800,NORMAL,false,,
                PO-2,WAW Central,Atlantis,2,800,NORMAL,false,,
                """);

        assertThatThrownBy(() -> reader.read(NOW))
                .isInstanceOf(SeedDataException.class)
                .hasMessage("seed/shipments.csv line 3: unknown warehouse 'Atlantis' (not in warehouses.csv)");
    }

    @Test
    void shipmentToItsOwnOriginFails() {
        SeedDataReader reader = readerWith(SeedDataReader.SHIPMENTS, """
                reference,origin,destination,pallets,weight_kg,priority,pinned_first,deadline_day,deadline_time
                PO-1,WAW Central,waw central,2,800,NORMAL,false,,
                """);

        assertThatThrownBy(() -> reader.read(NOW))
                .isInstanceOf(SeedDataException.class)
                .hasMessage("seed/shipments.csv line 2: origin and destination must differ (WAW Central)");
    }

    @Test
    void invalidShipmentValuesFailWithLine() {
        SeedDataReader badPallets = readerWith(SeedDataReader.SHIPMENTS, """
                reference,origin,destination,pallets,weight_kg,priority,pinned_first,deadline_day,deadline_time
                PO-1,WAW Central,KRK South,0,800,NORMAL,false,,
                """);
        SeedDataReader badPriority = readerWith(SeedDataReader.SHIPMENTS, """
                reference,origin,destination,pallets,weight_kg,priority,pinned_first,deadline_day,deadline_time
                PO-1,WAW Central,KRK South,2,800,LOW,false,,
                """);
        SeedDataReader halfDeadline = readerWith(SeedDataReader.SHIPMENTS, """
                reference,origin,destination,pallets,weight_kg,priority,pinned_first,deadline_day,deadline_time
                PO-1,WAW Central,KRK South,2,800,NORMAL,false,1,
                """);

        assertThatThrownBy(() -> badPallets.read(NOW))
                .hasMessage("seed/shipments.csv line 2: pallets must be greater than or equal to 1");
        assertThatThrownBy(() -> badPriority.read(NOW))
                .hasMessageContaining("line 2: priority must be URGENT, HIGH or NORMAL");
        assertThatThrownBy(() -> halfDeadline.read(NOW))
                .hasMessageContaining("line 2: deadline_day and deadline_time must both be set");
    }

    @Test
    void truckOutsideApiRangesFailsWithLine() {
        SeedDataReader reader = readerWith(SeedDataReader.TRUCKS, """
                plate,capacity_pallets,payload_kg,base_consumption_l_per_100km,extra_consumption_full_load_l_per_100km,driver_cost_pln_per_hour
                WX 1001A,41,24000,28,10,45
                """);

        assertThatThrownBy(() -> reader.read(NOW))
                .isInstanceOf(SeedDataException.class)
                .hasMessage("seed/trucks.csv line 2: capacityPallets must be less than or equal to 40");
    }

    @Test
    void duplicatePlateAfterNormalisationFails() {
        SeedDataReader reader = readerWith(SeedDataReader.TRUCKS, """
                plate,capacity_pallets,payload_kg,base_consumption_l_per_100km,extra_consumption_full_load_l_per_100km,driver_cost_pln_per_hour
                WX 1001A,33,24000,28,10,45
                wx  1001a,33,24000,28,10,45
                """);

        assertThatThrownBy(() -> reader.read(NOW))
                .hasMessage("seed/trucks.csv line 3: duplicate truck plate 'WX 1001A'");
    }

    @Test
    void malformedWarehouseRowsFail() {
        SeedDataReader wrongColumns = readerWith(SeedDataReader.WAREHOUSES, """
                name,city,lat,lon
                WAW Central,Warsaw,52,2297,21,0122
                """);
        SeedDataReader notANumber = readerWith(SeedDataReader.WAREHOUSES, """
                name,city,lat,lon
                WAW Central,Warsaw,north,21.0122
                """);
        SeedDataReader wrongHeader = readerWith(SeedDataReader.WAREHOUSES, """
                name;city;lat;lon
                """);

        assertThatThrownBy(() -> wrongColumns.read(NOW))
                .hasMessage("seed/warehouses.csv line 2: expected 4 columns but found 6");
        assertThatThrownBy(() -> notANumber.read(NOW))
                .hasMessage("seed/warehouses.csv line 2: lat is not a number: 'north'");
        assertThatThrownBy(() -> wrongHeader.read(NOW))
                .hasMessageContaining("seed/warehouses.csv: first line must be the header");
    }

    /** Bundled files, except {@code file}, which is replaced by {@code content}. */
    private static SeedDataReader readerWith(String file, String content) {
        return new SeedDataReader(validator, name -> name.equals(file)
                ? new StringReader(content)
                : new StringReader(bundled(name)));
    }

    private static String bundled(String name) throws IOException {
        try (InputStream in = SeedDataReaderTest.class.getResourceAsStream("/seed/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
