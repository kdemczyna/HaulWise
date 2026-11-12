package com.haulwise.solver.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PackingRecordsTest {

    private static final TruckSpec TRUCK = new TruckSpec(1, 10, 1_000, 28, 10, 45);

    @Test
    void priorityNaturalOrderIsMostImportantFirst() {
        assertThat(List.of(Priority.NORMAL, Priority.URGENT, Priority.HIGH).stream().sorted())
                .containsExactly(Priority.URGENT, Priority.HIGH, Priority.NORMAL);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shipmentRejectsNonPositivePallets(int pallets) {
        assertInvalid(() -> shipment(1, pallets, 100), "pallets");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shipmentRejectsNonPositiveWeight(int weightKg) {
        assertInvalid(() -> shipment(1, 1, weightKg), "weightKg");
    }

    @Test
    void shipmentRequiresAPriority() {
        assertThatThrownBy(() -> new ShipmentItem(1, 2, 1, 100, null, false, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("priority");
    }

    @Test
    void truckLoadSumsItsShipmentsAndCopiesTheList() {
        List<ShipmentItem> shipments = new ArrayList<>(List.of(shipment(1, 3, 250), shipment(2, 2, 250)));

        TruckLoad load = TruckLoad.of(TRUCK, shipments);
        shipments.clear();

        assertThat(load.shipments()).extracting(ShipmentItem::id).containsExactly(1L, 2L);
        assertThat(load.pallets()).isEqualTo(5);
        assertThat(load.weightKg()).isEqualTo(500);
        assertThat(load.palletsPct()).isCloseTo(50, within(1e-9));
        assertThat(load.weightPct()).isCloseTo(50, within(1e-9));
    }

    @Test
    void truckLoadRejectsTotalsThatDoNotMatchItsShipments() {
        assertInvalid(() -> new TruckLoad(TRUCK, List.of(shipment(1, 3, 250)), 4, 250), "pallets/weightKg");
    }

    @Test
    void truckLoadRejectsMorePalletsThanTheTruckHolds() {
        assertInvalid(() -> TruckLoad.of(TRUCK, List.of(shipment(1, 6, 100), shipment(2, 5, 100))), "exceeds truck 1");
    }

    @Test
    void truckLoadRejectsMoreWeightThanThePayload() {
        assertInvalid(() -> TruckLoad.of(TRUCK, List.of(shipment(1, 1, 1_001))), "exceeds truck 1");
    }

    @Test
    void unassignedRequiresReasonAndMessage() {
        assertThatThrownBy(() -> new Unassigned(1, null, "x")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new Unassigned(1, UnassignedReason.NO_CAPACITY_LEFT, null))
                .isInstanceOf(NullPointerException.class);
    }

    private static ShipmentItem shipment(long id, int pallets, int weightKg) {
        return new ShipmentItem(id, 2, pallets, weightKg, Priority.NORMAL, false, null);
    }

    private static void assertInvalid(ThrowingCallable callable, String messagePart) {
        assertThatThrownBy(callable).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(messagePart);
    }
}
