package com.haulwise.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.haulwise.solver.model.Priority;
import com.haulwise.solver.model.RouteStop;
import com.haulwise.solver.model.ShipmentItem;
import com.haulwise.solver.model.Stop;
import com.haulwise.solver.model.TruckLoad;
import com.haulwise.solver.model.TruckSpec;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class StopGrouperTest {

    private static final TruckSpec TRUCK = new TruckSpec(1, 33, 24_000, 28, 10, 45);
    private static final long GDN = 3;
    private static final long KRK = 2;
    private static final long LUB = 8;
    private static final LocalDateTime NOON = LocalDateTime.of(2026, 11, 13, 12, 0);
    private static final LocalDateTime EVENING = LocalDateTime.of(2026, 11, 13, 20, 0);

    private final StopGrouper grouper = new StopGrouper();

    @Test
    void twoShipmentsToTheSameDestinationBecomeOneStopWithSummedDrops() {
        TruckLoad load = TruckLoad.of(TRUCK, List.of(
                shipment(10, KRK, 4, 3_000, Priority.NORMAL, false, EVENING),
                shipment(11, KRK, 6, 5_000, Priority.HIGH, true, NOON)));

        List<Stop> stops = grouper.group(load);

        assertThat(stops).containsExactly(new Stop(KRK, List.of(11L, 10L), 10, 8_000, Priority.HIGH, true, NOON));
        assertThat(stops.get(0).toRouteStop()).isEqualTo(new RouteStop(KRK, 8_000));
    }

    @Test
    void stopsFollowTheirMostImportantShipmentInShipmentOrder() {
        TruckLoad load = TruckLoad.of(TRUCK, List.of(
                shipment(1, KRK, 10, 6_000, Priority.NORMAL, false, null),
                shipment(2, GDN, 2, 1_000, Priority.HIGH, true, null),
                shipment(3, LUB, 1, 500, Priority.URGENT, false, null),
                shipment(4, KRK, 3, 2_000, Priority.HIGH, true, null)));

        List<Stop> stops = grouper.group(load);

        // URGENT LUB, then the two HIGH pinned stops by pallets (KRK's shipment 4 has 3 > 2), shipment 1 joins KRK
        assertThat(stops).extracting(Stop::warehouseId).containsExactly(LUB, KRK, GDN);
        assertThat(stops.get(1).shipmentIds()).containsExactly(4L, 1L);
        assertThat(stops.get(1).maxPriority()).isEqualTo(Priority.HIGH);
        assertThat(stops).extracting(Stop::pinned).containsExactly(false, true, true);
    }

    @Test
    void pinWinnerIsTheDestinationOfTheFirstPinnedShipmentNotTheFirstPinnedStop() {
        // Stop KRK = URGENT unpinned + NORMAL pinned, stop GDN = HIGH pinned. KRK ranks first as a stop
        // (because of its URGENT shipment), but the first pinned shipment is the HIGH one to GDN.
        TruckLoad load = TruckLoad.of(TRUCK, List.of(
                shipment(1, KRK, 1, 100, Priority.URGENT, false, null),
                shipment(2, KRK, 1, 100, Priority.NORMAL, true, null),
                shipment(3, GDN, 1, 100, Priority.HIGH, true, null)));

        List<Stop> stops = grouper.group(load);

        assertThat(stops).extracting(Stop::warehouseId).containsExactly(KRK, GDN);
        assertThat(stops).extracting(Stop::pinned).containsExactly(true, true);
        assertThat(grouper.pinnedWinner(load)).hasValue(GDN);
    }

    @Test
    void noPinnedShipmentMeansNoPinWinner() {
        TruckLoad load = TruckLoad.of(TRUCK, List.of(shipment(1, KRK, 1, 100, Priority.URGENT, false, null)));

        assertThat(grouper.pinnedWinner(load)).isEmpty();
    }

    @Test
    void resultDoesNotDependOnTheLoadingOrder() {
        ShipmentItem a = shipment(1, KRK, 10, 6_000, Priority.NORMAL, false, NOON);
        ShipmentItem b = shipment(2, GDN, 2, 1_000, Priority.HIGH, false, null);
        ShipmentItem c = shipment(3, KRK, 1, 500, Priority.URGENT, false, EVENING);

        assertThat(grouper.group(TruckLoad.of(TRUCK, List.of(a, b, c))))
                .isEqualTo(grouper.group(TruckLoad.of(TRUCK, List.of(c, b, a))));
    }

    @Test
    void stopWithoutDeadlinesHasNoEarliestDeadline() {
        List<Stop> stops = grouper.group(TruckLoad.of(TRUCK, List.of(
                shipment(1, GDN, 1, 100, Priority.NORMAL, false, null),
                shipment(2, GDN, 1, 100, Priority.NORMAL, false, null))));

        assertThat(stops).singleElement().satisfies(stop -> {
            assertThat(stop.earliestDeadline()).isNull();
            assertThat(stop.pinned()).isFalse();
            assertThat(stop.shipmentIds()).containsExactly(1L, 2L);
        });
    }

    @Test
    void emptyLoadHasNoStops() {
        assertThat(grouper.group(TruckLoad.of(TRUCK, List.of()))).isEmpty();
    }

    @Test
    void stopRejectsMissingShipmentsAndEmptyDrops() {
        assertThatThrownBy(() -> new Stop(GDN, List.of(), 1, 1, Priority.NORMAL, false, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Stop(GDN, List.of(1L), 0, 1, Priority.NORMAL, false, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Stop(GDN, List.of(1L), 1, 0, Priority.NORMAL, false, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ShipmentItem shipment(long id, long destinationId, int pallets, int weightKg, Priority priority,
            boolean pinned, LocalDateTime deadline) {
        return new ShipmentItem(id, destinationId, pallets, weightKg, priority, pinned, deadline);
    }
}
