package com.haulwise.solver;

import static com.haulwise.solver.SeedPlanScenario.GDN;
import static com.haulwise.solver.SeedPlanScenario.KRK;
import static com.haulwise.solver.SeedPlanScenario.KTW;
import static com.haulwise.solver.SeedPlanScenario.LDZ;
import static com.haulwise.solver.SeedPlanScenario.LUB;
import static com.haulwise.solver.SeedPlanScenario.POZ;
import static com.haulwise.solver.SeedPlanScenario.SZZ;
import static com.haulwise.solver.SeedPlanScenario.WRO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.haulwise.solver.model.Leg;
import com.haulwise.solver.model.PlanVariant;
import com.haulwise.solver.model.SolverResult;
import com.haulwise.solver.model.UnassignedReason;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * End to end on the WAW seed demo ({@link SeedPlanScenario}): T-09 packs trucks 1, 2 and 5; truck 1 carries
 * the URGENT Lublin, the pinned HIGH Gdansk, the HIGH Krakow and Katowice. Cost-optimal visits Lublin first and
 * Gdansk last; PRIORITY must start with the pinned Gdansk, which costs the extra kilometres. Trucks 2 and 5 have
 * only NORMAL stops, so both variants agree there. Arrival offsets are minutes after departure (no service at
 * the first stop, 30 min at each earlier one).
 */
class PlannerSeedTest {

    private final Planner planner = new Planner();

    /**
     * Truck 1 with return: OPTIMAL WAW-LUB-KRK-KTW-GDN-WAW = 170 + 270 + 80 + 540 + 340 = 1400 km; PRIORITY
     * WAW-GDN-LUB-KRK-KTW-WAW = 340 + 505 + 270 + 80 + 290 = 1485 km: +85 km, 75 more driving minutes.
     */
    @Test
    void withReturnToDepotTheGdanskPinCosts85Km() {
        SolverResult result = planner.plan(SeedPlanScenario.input(true));

        assertThat(truckIds(result.optimal())).containsExactly(1L, 2L, 5L);
        assertThat(stops(result.optimal())).containsExactly(
                List.of(LUB, KRK, KTW, GDN), List.of(POZ, KRK), List.of(LDZ, WRO, SZZ));
        assertThat(stops(result.priority())).containsExactly(
                List.of(GDN, LUB, KRK, KTW), List.of(POZ, KRK), List.of(LDZ, WRO, SZZ));
        assertThat(arrivals(result.optimal(), 0)).containsExactly(146.0, 446.0, 545.0, 1038.0);
        assertThat(arrivals(result.priority(), 0)).containsExactly(291.0, 756.0, 1056.0, 1155.0);

        assertThat(result.optimal().totals().distanceKm()).isCloseTo(3750, within(1e-9));
        assertThat(result.priority().totals().distanceKm()).isCloseTo(3835, within(1e-9));
        assertThat(result.optimal().totals().costPln()).isCloseTo(9849.88, within(0.005));
        assertThat(result.priority().totals().costPln()).isCloseTo(10142.86, within(0.005));
        assertThat(result.delta().distanceKm()).isCloseTo(85, within(1e-9));
        assertThat(result.delta().durationMinutes()).isCloseTo(75, within(1e-9));
        assertThat(result.delta().litres()).isCloseTo(38.18, within(0.005));
        assertThat(result.delta().driverCostPln()).isCloseTo(56.25, within(1e-9));
        assertThat(result.delta().costPln()).isCloseTo(292.97, within(0.005));
        assertThat(result.delta().sentence())
                .isEqualTo("Delivering Gdansk first costs 292.97 PLN more (85 km and 1 h 15 min longer).");

        assertThat(result.unassigned()).singleElement().satisfies(unassigned -> {
            assertThat(unassigned.shipmentId()).isEqualTo(1099);
            assertThat(unassigned.reason()).isEqualTo(UnassignedReason.EXCEEDS_LARGEST_TRUCK);
        });
        assertThat(result.warnings()).isEmpty();
    }

    /**
     * Without return the last stop matters more: OPTIMAL truck 1 = 170 + 270 + 80 + 540 = 1060 km, PRIORITY
     * 340 + 505 + 270 + 80 = 1195 km: +135 km, 117 more minutes; truck 2 now goes to Krakow first.
     */
    @Test
    void withoutReturnTheGdanskPinCosts135Km() {
        SolverResult result = planner.plan(SeedPlanScenario.input(false));

        assertThat(stops(result.optimal())).containsExactly(
                List.of(LUB, KRK, KTW, GDN), List.of(KRK, POZ), List.of(LDZ, WRO, SZZ));
        assertThat(stops(result.priority())).containsExactly(
                List.of(GDN, LUB, KRK, KTW), List.of(KRK, POZ), List.of(LDZ, WRO, SZZ));
        assertThat(result.delta().distanceKm()).isCloseTo(135, within(1e-9));
        assertThat(result.delta().durationMinutes()).isCloseTo(117, within(1e-9));
        assertThat(result.delta().costPln()).isCloseTo(411.27, within(0.005));
        assertThat(result.delta().sentence())
                .isEqualTo("Delivering Gdansk first costs 411.27 PLN more (135 km and 1 h 57 min longer).");
    }

    private static List<Long> truckIds(PlanVariant variant) {
        return variant.routes().stream().map(route -> route.truck().id()).toList();
    }

    private static List<List<Long>> stops(PlanVariant variant) {
        return variant.routes().stream().map(route -> route.route().warehouseIds()).toList();
    }

    private static List<Double> arrivals(PlanVariant variant, int route) {
        return variant.routes().get(route).route().evaluation().stopLegs().stream().map(Leg::arrivalMinutes).toList();
    }
}
