package com.haulwise.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.haulwise.dto.plan.PlanWarningCode;
import com.haulwise.dto.plan.WarningResponse;
import com.haulwise.dto.plan.WarningSeverity;
import com.haulwise.service.PlanWarnings.LateShipment;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PlanWarningsTest {

    private static final LocalDateTime NOON = LocalDateTime.of(2026, 11, 13, 12, 0);

    @Test
    void pinConflictNamesPlateKeptCityAndDroppedReference() {
        WarningResponse warning = PlanWarnings.pinConflict(1, "WX 1001A", "Gdansk", List.of("PO-1050"), List.of(10L));

        assertThat(warning.code()).isEqualTo(PlanWarningCode.PIN_CONFLICT);
        assertThat(warning.severity()).isEqualTo(WarningSeverity.WARNING);
        assertThat(warning.truckId()).isEqualTo(1L);
        assertThat(warning.shipmentIds()).containsExactly(10L);
        assertThat(warning.message()).isEqualTo("Truck WX 1001A has more than one pinned stop: Gdansk stays first; "
                + "the pin of PO-1050 was dropped, so it is ordered by priority.");
    }

    @Test
    void pinConflictWithSeveralDroppedPinsUsesThePlural() {
        WarningResponse warning = PlanWarnings.pinConflict(1, "WX 1001A", "Gdansk",
                List.of("PO-1", "PO-2", "PO-3"), List.of(1L, 2L, 3L));

        assertThat(warning.message()).isEqualTo("Truck WX 1001A has more than one pinned stop: Gdansk stays first; "
                + "the pins of PO-1, PO-2 and PO-3 were dropped, so they are ordered by priority.");
    }

    @Test
    void deadlineMissedListsEachLateShipmentWithItsDeadline() {
        WarningResponse warning = PlanWarnings.deadlineMissed(2, "WX 2002B", "Lublin", NOON.plusHours(3),
                List.of(new LateShipment(5, "PO-5", NOON), new LateShipment(6, "PO-6", NOON.plusHours(1))));

        assertThat(warning.code()).isEqualTo(PlanWarningCode.DEADLINE_MISSED);
        assertThat(warning.severity()).isEqualTo(WarningSeverity.WARNING);
        assertThat(warning.shipmentIds()).containsExactly(5L, 6L);
        assertThat(warning.message()).isEqualTo("WX 2002B arrives in Lublin at 2026-11-13 15:00, after the "
                + "deadlines of PO-5 (2026-11-13 12:00) and PO-6 (2026-11-13 13:00).");
    }

    @Test
    void estimatedDistanceIsInfoAndPlanWide() {
        WarningResponse warning = PlanWarnings.estimatedDistance(List.of("A -> B"));

        assertThat(warning.code()).isEqualTo(PlanWarningCode.ESTIMATED_DISTANCE);
        assertThat(warning.severity()).isEqualTo(WarningSeverity.INFO);
        assertThat(warning.truckId()).isNull();
        assertThat(warning.shipmentIds()).isEmpty();
        assertThat(warning.message()).isEqualTo("1 leg uses an estimated distance (straight line x road factor), "
                + "so its time and cost are approximate: A -> B.");
    }

    @Test
    void estimatedDistanceListsAtMostFiveLegs() {
        List<String> legs = IntStream.rangeClosed(1, 7).mapToObj(i -> "L" + i).toList();

        assertThat(PlanWarnings.estimatedDistance(legs).message()).isEqualTo("7 legs use estimated distances "
                + "(straight line x road factor), so their times and costs are approximate: "
                + "L1, L2, L3, L4, L5 and 2 more.");
    }

    @Test
    void joinWithAnd() {
        assertThat(PlanWarnings.joinWithAnd(List.of("A"))).isEqualTo("A");
        assertThat(PlanWarnings.joinWithAnd(List.of("A", "B"))).isEqualTo("A and B");
        assertThat(PlanWarnings.joinWithAnd(List.of("A", "B", "C"))).isEqualTo("A, B and C");
    }
}
