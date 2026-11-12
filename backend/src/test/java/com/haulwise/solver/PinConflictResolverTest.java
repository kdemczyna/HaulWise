package com.haulwise.solver;

import static com.haulwise.solver.TestMatrices.lineMatrix;
import static org.assertj.core.api.Assertions.assertThat;

import com.haulwise.solver.model.CostParameters;
import com.haulwise.solver.model.PinResolution;
import com.haulwise.solver.model.Priority;
import com.haulwise.solver.model.PriorityWeights;
import com.haulwise.solver.model.ShipmentItem;
import com.haulwise.solver.model.Stop;
import com.haulwise.solver.model.TruckLoad;
import com.haulwise.solver.model.TruckSpec;
import com.haulwise.solver.model.Warning;
import com.haulwise.solver.model.WarningCode;
import java.util.List;
import org.junit.jupiter.api.Test;

class PinConflictResolverTest {

    private static final TruckSpec TRUCK = new TruckSpec(5, 33, 24_000, 10, 0, 0);

    private final StopGrouper grouper = new StopGrouper();
    private final PinConflictResolver resolver = new PinConflictResolver(grouper);

    @Test
    void withoutPinsTheStopsAreTheGroupedStopsAndThereIsNoWarning() {
        TruckLoad load = TruckLoad.of(TRUCK, List.of(
                shipment(1, 2, Priority.URGENT, false), shipment(2, 3, Priority.NORMAL, false)));

        PinResolution resolution = resolver.resolve(load);

        assertThat(resolution.stops()).isEqualTo(grouper.group(load));
        assertThat(resolution.warnings()).isEmpty();
    }

    @Test
    void severalPinnedShipmentsToTheSameDestinationAreNoConflict() {
        TruckLoad load = TruckLoad.of(TRUCK, List.of(
                shipment(1, 2, Priority.NORMAL, true), shipment(2, 2, Priority.HIGH, true),
                shipment(3, 3, Priority.NORMAL, false)));

        PinResolution resolution = resolver.resolve(load);

        assertThat(resolution.stops()).isEqualTo(grouper.group(load));
        assertThat(resolution.stops()).filteredOn(Stop::pinned).extracting(Stop::warehouseId).containsExactly(2L);
        assertThat(resolution.warnings()).isEmpty();
    }

    @Test
    void conflictKeepsThePinnedWinnerAndWarnsAboutTheDroppedPins() {
        // Pinned: 2 -> warehouse 3 (NORMAL), 3 -> warehouse 4 (HIGH), 5 -> warehouse 2 (NORMAL, bigger than 2).
        // By the shipment order (HIGH before NORMAL) shipment 3 wins, so warehouse 4 stays pinned; the URGENT
        // but unpinned shipment 1 does not count. Dropped pins in shipment order: 5 (3 pallets), 2 (1 pallet).
        TruckLoad load = TruckLoad.of(TRUCK, List.of(
                shipment(1, 2, Priority.URGENT, false),
                shipment(2, 3, Priority.NORMAL, true),
                shipment(3, 4, Priority.HIGH, true),
                shipment(4, 4, Priority.NORMAL, false),
                new ShipmentItem(5, 2, 3, 300, Priority.NORMAL, true, null)));

        PinResolution resolution = resolver.resolve(load);

        assertThat(grouper.pinnedWinner(load)).hasValue(4L);
        assertThat(resolution.stops()).extracting(Stop::warehouseId)
                .containsExactlyElementsOf(grouper.group(load).stream().map(Stop::warehouseId).toList());
        assertThat(resolution.stops()).filteredOn(Stop::pinned).extracting(Stop::warehouseId).containsExactly(4L);
        assertThat(resolution.warnings()).singleElement().satisfies((Warning warning) -> {
            assertThat(warning.code()).isEqualTo(WarningCode.PIN_CONFLICT);
            assertThat(warning.truckId()).isEqualTo(5L);
            assertThat(warning.shipmentIds()).containsExactly(5L, 2L);
            assertThat(warning.message()).isNotBlank();
        });
    }

    @Test
    void resolvedStopsCanBeSequencedWithTheWinnerFirst() {
        // Line: depot 1 at 0, 2 at +10 km, 3 at -15 km, 4 at +30 km
        TruckLoad load = TruckLoad.of(TRUCK, List.of(
                shipment(1, 2, Priority.NORMAL, true), shipment(2, 3, Priority.HIGH, true),
                shipment(3, 4, Priority.NORMAL, false)));

        List<Long> order = new StopSequencer(lineMatrix(0, 1, -1.5, 3), new CostParameters(10, 0))
                .sequence(1, resolver.resolve(load).stops(), TRUCK, false,
                        new PriorityObjective(new PriorityWeights(150, 50)))
                .warehouseIds();

        assertThat(order).first().isEqualTo(3L);
    }

    private static ShipmentItem shipment(long id, long destinationId, Priority priority, boolean pinned) {
        return new ShipmentItem(id, destinationId, 1, 100, priority, pinned, null);
    }
}
