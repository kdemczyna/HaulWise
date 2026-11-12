package com.haulwise.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import com.haulwise.solver.model.PackingResult;
import com.haulwise.solver.model.Priority;
import com.haulwise.solver.model.ShipmentItem;
import com.haulwise.solver.model.TruckLoad;
import com.haulwise.solver.model.TruckSpec;
import com.haulwise.solver.model.Unassigned;
import com.haulwise.solver.model.UnassignedReason;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Hand-checkable packing cases. Shipments go to destination 99 unless the case needs otherwise; the
 * destination does not influence packing (D-03).
 */
class BinPackerTest {

    private static final long DESTINATION = 99;

    private final BinPacker packer = new BinPacker();

    @Test
    void exactFitFillsTheTruckToOneHundredPercent() {
        TruckSpec truck = truck(1, 10, 1_000);

        PackingResult result = packer.pack(
                List.of(normal(1, 6, 600), normal(2, 4, 400)), List.of(truck));

        assertThat(result.unassigned()).isEmpty();
        assertThat(result.loads()).singleElement().satisfies(load -> {
            assertThat(ids(load)).containsExactly(1L, 2L);
            assertThat(load.pallets()).isEqualTo(10);
            assertThat(load.weightKg()).isEqualTo(1_000);
            assertThat(load.palletsPct()).isCloseTo(100, within(1e-9));
            assertThat(load.weightPct()).isCloseTo(100, within(1e-9));
        });
    }

    @Test
    void palletLimitedTruckRejectsAShipmentThatWouldStillFitByWeight() {
        PackingResult result = packer.pack(
                List.of(normal(1, 6, 100), normal(2, 6, 100)), List.of(truck(1, 10, 10_000)));

        assertThat(loadedIds(result)).containsExactly(List.of(1L));
        assertThat(result.unassigned()).singleElement().satisfies(unassigned -> {
            assertThat(unassigned.shipmentId()).isEqualTo(2);
            assertThat(unassigned.reason()).isEqualTo(UnassignedReason.NO_CAPACITY_LEFT);
        });
    }

    @Test
    void weightLimitedTruckRejectsAShipmentThatWouldStillFitByPallets() {
        PackingResult result = packer.pack(
                List.of(normal(1, 2, 600), normal(2, 2, 500)), List.of(truck(1, 33, 1_000)));

        assertThat(loadedIds(result)).containsExactly(List.of(1L));
        assertThat(reasons(result)).containsExactly(UnassignedReason.NO_CAPACITY_LEFT);
    }

    @Test
    void shipmentThatDoesNotFitTheFirstTruckGoesToTheNextOne() {
        PackingResult result = packer.pack(
                List.of(normal(1, 2, 600), normal(2, 2, 500)),
                List.of(truck(1, 33, 1_000), truck(2, 18, 1_000)));

        assertThat(result.loads()).extracting(load -> load.truck().id()).containsExactly(1L, 2L);
        assertThat(loadedIds(result)).containsExactly(List.of(1L), List.of(2L));
        assertThat(result.unassigned()).isEmpty();
    }

    @Test
    void urgentShipmentGetsCapacityBeforeABiggerNormalOne() {
        ShipmentItem bigNormal = item(1, 8, 800, Priority.NORMAL, false);
        ShipmentItem high = item(2, 1, 100, Priority.HIGH, false);
        ShipmentItem smallUrgent = item(3, 4, 400, Priority.URGENT, false);

        PackingResult result = packer.pack(List.of(bigNormal, high, smallUrgent), List.of(truck(1, 10, 10_000)));

        assertThat(loadedIds(result)).containsExactly(List.of(3L, 2L));
        assertThat(result.unassigned()).singleElement().satisfies(unassigned -> {
            assertThat(unassigned.shipmentId()).isEqualTo(1);
            assertThat(unassigned.reason()).isEqualTo(UnassignedReason.NO_CAPACITY_LEFT);
            assertThat(unassigned.message()).isEqualTo(
                    "No capacity left for 8 pallets / 800 kg: the selected trucks are already loaded.");
        });
    }

    @Test
    void pinnedShipmentGoesBeforeABiggerOneOfTheSamePriority() {
        ShipmentItem bigHigh = item(1, 8, 800, Priority.HIGH, false);
        ShipmentItem pinnedHigh = item(2, 4, 400, Priority.HIGH, true);

        PackingResult result = packer.pack(List.of(bigHigh, pinnedHigh), List.of(truck(1, 10, 10_000)));

        assertThat(loadedIds(result)).containsExactly(List.of(2L));
        assertThat(result.unassigned()).extracting(Unassigned::shipmentId).containsExactly(1L);
    }

    @Test
    void priorityBeatsPin() {
        ShipmentItem bigHigh = item(1, 8, 800, Priority.HIGH, false);
        ShipmentItem pinnedNormal = item(2, 4, 400, Priority.NORMAL, true);

        PackingResult result = packer.pack(List.of(pinnedNormal, bigHigh), List.of(truck(1, 10, 10_000)));

        assertThat(loadedIds(result)).containsExactly(List.of(1L));
        assertThat(result.unassigned())
                .extracting(Unassigned::shipmentId, Unassigned::reason)
                .containsExactly(tuple(2L, UnassignedReason.NO_CAPACITY_LEFT));
    }

    @Test
    void messagesUseTheSingularForOnePallet() {
        List<ShipmentItem> shipments = List.of(normal(1, 1, 500), normal(2, 1, 600), normal(3, 2, 100));

        PackingResult result = packer.pack(shipments, List.of(truck(1, 1, 1_000)));

        assertThat(result.unassigned()).containsExactly(
                new Unassigned(3, UnassignedReason.EXCEEDS_LARGEST_TRUCK,
                        "2 pallets exceed the largest selected truck (1 pallet)."),
                new Unassigned(1, UnassignedReason.NO_CAPACITY_LEFT,
                        "No capacity left for 1 pallet / 500 kg: the selected trucks are already loaded."));
    }

    @Test
    void palletLimitIsReportedWhenBothLimitsAreExceeded() {
        PackingResult result = packer.pack(List.of(normal(1, 40, 30_000)), List.of(truck(1, 33, 24_000)));

        assertThat(result.unassigned()).extracting(Unassigned::message)
                .containsExactly("40 pallets exceed the largest selected truck (33 pallets).");
    }

    @Test
    void largerShipmentsArePackedFirstWithinTheSamePriority() {
        // In input order (3, 4, 6, 7) first fit would strand the 7; decreasing order packs both trucks full.
        List<ShipmentItem> shipments = List.of(
                normal(1, 3, 300), normal(2, 4, 400), normal(3, 6, 600), normal(4, 7, 700));

        PackingResult result = packer.pack(shipments, List.of(truck(1, 10, 10_000), truck(2, 10, 10_000)));

        assertThat(loadedIds(result)).containsExactly(List.of(4L, 1L), List.of(3L, 2L));
        assertThat(result.loads()).extracting(TruckLoad::pallets).containsExactly(10, 10);
        assertThat(result.unassigned()).isEmpty();
    }

    @Test
    void heavierShipmentGoesFirstWhenPalletsAreEqual() {
        List<ShipmentItem> shipments = List.of(normal(1, 2, 300), normal(2, 2, 700), normal(3, 2, 400));

        PackingResult result = packer.pack(shipments, List.of(truck(1, 4, 1_000)));

        assertThat(loadedIds(result)).containsExactly(List.of(2L, 1L));
        assertThat(result.unassigned()).extracting(Unassigned::shipmentId).containsExactly(3L);
    }

    @Test
    void lowerIdWinsBetweenIdenticalShipments() {
        PackingResult result = packer.pack(
                List.of(normal(5, 6, 600), normal(3, 6, 600)), List.of(truck(1, 10, 10_000)));

        assertThat(loadedIds(result)).containsExactly(List.of(3L));
        assertThat(result.unassigned()).extracting(Unassigned::shipmentId).containsExactly(5L);
    }

    @Test
    void biggerTruckIsFilledFirstAndEqualTrucksByLowerId() {
        List<TruckSpec> trucks = List.of(
                truck(7, 18, 12_000), truck(4, 33, 24_000), truck(2, 33, 24_000), truck(9, 33, 30_000));
        List<ShipmentItem> shipments = List.of(
                normal(1, 33, 24_000), normal(2, 33, 24_000), normal(3, 33, 24_000), normal(4, 18, 12_000));

        PackingResult result = packer.pack(shipments, trucks);

        assertThat(result.loads()).extracting(load -> load.truck().id()).containsExactly(9L, 2L, 4L, 7L);
        assertThat(loadedIds(result)).containsExactly(List.of(1L), List.of(2L), List.of(3L), List.of(4L));
    }

    @Test
    void shipmentWithMorePalletsThanAnyTruckExceedsTheLargestTruck() {
        PackingResult result = packer.pack(
                List.of(normal(14, 36, 21_000)), List.of(truck(1, 33, 24_000), truck(2, 18, 12_000)));

        assertThat(result.loads()).isEmpty();
        assertThat(result.unassigned()).containsExactly(new Unassigned(14, UnassignedReason.EXCEEDS_LARGEST_TRUCK,
                "36 pallets exceed the largest selected truck (33 pallets)."));
    }

    @Test
    void shipmentHeavierThanAnyPayloadExceedsTheLargestTruck() {
        PackingResult result = packer.pack(List.of(normal(1, 10, 30_000)), List.of(truck(1, 33, 24_000)));

        assertThat(result.unassigned()).containsExactly(new Unassigned(1, UnassignedReason.EXCEEDS_LARGEST_TRUCK,
                "30000 kg exceed the largest payload of the selected trucks (24000 kg)."));
    }

    @Test
    void shipmentFittingEachLimitOnSomeTruckButNoTruckOnBothExceedsTheLargestTruck() {
        // 20 pallets fit only the 33-pallet truck, 15000 kg fit only the 24000 kg truck.
        List<TruckSpec> trucks = List.of(truck(1, 33, 10_000), truck(2, 18, 24_000));

        PackingResult result = packer.pack(List.of(normal(1, 20, 15_000)), trucks);

        assertThat(result.unassigned()).containsExactly(new Unassigned(1, UnassignedReason.EXCEEDS_LARGEST_TRUCK,
                "20 pallets / 15000 kg do not fit any single selected truck."));
    }

    @Test
    void oversizedShipmentIsReportedAsExceedingEvenWhenTheTrucksAreAlsoFull() {
        List<ShipmentItem> shipments = List.of(normal(1, 10, 1_000), normal(2, 11, 1_000), normal(3, 5, 500));

        PackingResult result = packer.pack(shipments, List.of(truck(1, 10, 10_000)));

        assertThat(loadedIds(result)).containsExactly(List.of(1L));
        assertThat(result.unassigned())
                .extracting(Unassigned::shipmentId, Unassigned::reason)
                .containsExactly(
                        tuple(2L, UnassignedReason.EXCEEDS_LARGEST_TRUCK),
                        tuple(3L, UnassignedReason.NO_CAPACITY_LEFT));
    }

    @Test
    void withoutTrucksEveryShipmentIsUnassigned() {
        PackingResult result = packer.pack(List.of(normal(1, 1, 100), normal(2, 2, 200)), List.of());

        assertThat(result.loads()).isEmpty();
        assertThat(result.unassigned()).containsExactly(
                new Unassigned(2, UnassignedReason.EXCEEDS_LARGEST_TRUCK, "No trucks are selected."),
                new Unassigned(1, UnassignedReason.EXCEEDS_LARGEST_TRUCK, "No trucks are selected."));
    }

    @Test
    void withoutShipmentsNothingIsLoaded() {
        PackingResult result = packer.pack(List.of(), List.of(truck(1, 33, 24_000)));

        assertThat(result.loads()).isEmpty();
        assertThat(result.unassigned()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 42, 2026})
    void resultDoesNotDependOnInputOrder(long seed) {
        List<ShipmentItem> shipments = seedDemoShipments();
        List<TruckSpec> trucks = seedTrucks();
        PackingResult expected = packer.pack(shipments, trucks);

        Random random = new Random(seed);
        List<ShipmentItem> shuffledShipments = new ArrayList<>(shipments);
        List<TruckSpec> shuffledTrucks = new ArrayList<>(trucks);
        Collections.shuffle(shuffledShipments, random);
        Collections.shuffle(shuffledTrucks, random);

        assertThat(packer.pack(shuffledShipments, shuffledTrucks)).isEqualTo(expected);
    }

    /**
     * The 11 WAW shipments of {@code seed/shipments.csv} (ids = PO numbers) on the 5 seed trucks
     * (ARCHITECTURE 4.1). Expected first-fit-decreasing trace, trucks tried as 1, 2, 5 (33 pal / 24000 kg),
     * 3 (18 / 12000), 4 (10 / 6000):
     * 1041 U 6 -> T1 (6); 1042 H pinned 10 -> T1 (16); 1043 H 6 -> T1 (22); 1099 36 -> exceeds;
     * 1044 16 -> T2 (16); 1045 14 -> T2 (30); 1046 12 -> T5 (12); 1047 8 -> T1 (30); 1048 5 -> T5 (17);
     * 1049 4 -> T5 (21); 1050 2 -> T1 (32).
     */
    @Test
    void seedDemoFitsOnThreeBigTrucksAndReportsTheOversizedShipment() {
        PackingResult result = packer.pack(seedDemoShipments(), seedTrucks());

        assertThat(result.loads()).extracting(load -> load.truck().id()).containsExactly(1L, 2L, 5L);
        assertThat(loadedIds(result)).containsExactly(
                List.of(1041L, 1042L, 1043L, 1047L, 1050L),
                List.of(1044L, 1045L),
                List.of(1046L, 1048L, 1049L));
        assertThat(result.loads()).extracting(TruckLoad::pallets).containsExactly(32, 30, 21);
        assertThat(result.loads()).extracting(TruckLoad::weightKg).containsExactly(20_200, 20_800, 13_100);
        assertThat(result.loads().get(0).palletsPct()).isCloseTo(100.0 * 32 / 33, within(1e-9));
        assertThat(result.loads().get(0).weightPct()).isCloseTo(100.0 * 20_200 / 24_000, within(1e-9));
        assertThat(result.unassigned()).containsExactly(new Unassigned(1099, UnassignedReason.EXCEEDS_LARGEST_TRUCK,
                "36 pallets exceed the largest selected truck (33 pallets)."));
    }

    @Test
    void resultListsAreUnmodifiable() {
        PackingResult result = packer.pack(
                List.of(normal(1, 1, 100), normal(2, 50, 100)), List.of(truck(1, 10, 1_000)));

        assertThatThrownBy(() -> result.loads().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.unassigned().clear()).isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> result.loads().get(0).shipments().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void rejectsDuplicateShipmentIds() {
        assertThatThrownBy(() -> packer.pack(
                List.of(normal(1, 1, 100), normal(1, 2, 200)), List.of(truck(1, 10, 1_000))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate shipment id 1");
    }

    @Test
    void rejectsDuplicateTruckIds() {
        assertThatThrownBy(() -> packer.pack(
                List.of(normal(1, 1, 100)), List.of(truck(3, 10, 1_000), truck(3, 20, 2_000))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate truck id 3");
    }

    private static List<ShipmentItem> seedDemoShipments() {
        // PO numbers stand in for the seeded ids 1..14; both orders agree and no tie reaches the id tie-break.
        return List.of(
                item(1041, 6, 3_600, Priority.URGENT, false),
                item(1042, 10, 6_500, Priority.HIGH, true),
                item(1043, 6, 4_100, Priority.HIGH, false),
                normal(1044, 16, 11_000),
                normal(1045, 14, 9_800),
                normal(1046, 12, 7_400),
                normal(1047, 8, 5_200),
                normal(1048, 5, 3_100),
                normal(1049, 4, 2_600),
                normal(1050, 2, 800),
                normal(1099, 36, 21_000));
    }

    private static List<TruckSpec> seedTrucks() {
        return List.of(
                truck(1, 33, 24_000), truck(2, 33, 24_000), truck(3, 18, 12_000), truck(4, 10, 6_000),
                truck(5, 33, 24_000));
    }

    private static List<List<Long>> loadedIds(PackingResult result) {
        return result.loads().stream().map(BinPackerTest::ids).toList();
    }

    private static List<Long> ids(TruckLoad load) {
        return load.shipments().stream().map(ShipmentItem::id).toList();
    }

    private static List<UnassignedReason> reasons(PackingResult result) {
        return result.unassigned().stream().map(Unassigned::reason).toList();
    }

    private static ShipmentItem normal(long id, int pallets, int weightKg) {
        return item(id, pallets, weightKg, Priority.NORMAL, false);
    }

    private static ShipmentItem item(long id, int pallets, int weightKg, Priority priority, boolean pinned) {
        return new ShipmentItem(id, DESTINATION, pallets, weightKg, priority, pinned, null);
    }

    private static TruckSpec truck(long id, int capacityPallets, int payloadKg) {
        return new TruckSpec(id, capacityPallets, payloadKg, 28, 10, 45);
    }
}
