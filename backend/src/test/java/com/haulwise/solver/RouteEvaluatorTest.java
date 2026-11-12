package com.haulwise.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.haulwise.solver.model.CostParameters;
import com.haulwise.solver.model.DirectedPair;
import com.haulwise.solver.model.DistanceMatrix;
import com.haulwise.solver.model.DistanceSource;
import com.haulwise.solver.model.Leg;
import com.haulwise.solver.model.LegDistance;
import com.haulwise.solver.model.RouteEvaluation;
import com.haulwise.solver.model.RouteStop;
import com.haulwise.solver.model.Totals;
import com.haulwise.solver.model.TruckSpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Hand-computed examples. Fixture: depot 1, stops A = 2 and B = 3.
 * Legs (both directions): 1-2 100 km / 60 min, 2-3 50 km / 45 min, 3-1 200 km / 150 min.
 * Truck: payload 24 000 kg, 28 L/100 km empty + 10 L/100 km at full load, driver 45 PLN/h.
 * Settings: diesel 6.00 PLN/L, 30 min service per stop.
 */
class RouteEvaluatorTest {

    private static final Offset<Double> EXACT = within(1e-9);
    private static final long DEPOT = 1;
    private static final long A = 2;
    private static final long B = 3;

    private static final TruckSpec TRUCK = new TruckSpec(7, 33, 24_000, 28, 10, 45);
    private static final CostParameters PARAMETERS = new CostParameters(6.00, 30);

    private final DistanceMatrix matrix = symmetricMatrix(
            leg(DEPOT, A, 100, 60),
            leg(A, B, 50, 45),
            leg(B, DEPOT, 200, 150));
    private final RouteEvaluator evaluator = new RouteEvaluator(matrix, PARAMETERS);

    @Test
    void halfLoadMatchesTheArchitectureWorkedCheck() {
        Leg leg = firstLeg(evaluator, 12_000, TRUCK);

        // 100 / 100 x (28 + 10 x 12000 / 24000) = 33.0 L
        assertThat(leg.litres()).isCloseTo(33.0, EXACT);
    }

    @Test
    void fullTruckBurnsBasePlusExtraAndEmptyTruckBurnsBase() {
        Leg full = firstLeg(evaluator, 24_000, TRUCK);
        Leg empty = firstLeg(evaluator, 0, TRUCK);

        assertThat(full.litres()).isCloseTo(38.0, EXACT);       // 100 km x (28 + 10) / 100
        assertThat(full.fuelCostPln()).isCloseTo(228.0, EXACT); // 38 x 6
        assertThat(empty.litres()).isCloseTo(28.0, EXACT);      // 100 km x 28 / 100
        assertThat(empty.fuelCostPln()).isCloseTo(168.0, EXACT);
    }

    @Test
    void twoStopRouteWithReturnDecreasesLoadAfterEachDrop() {
        RouteEvaluation route = evaluator.evaluate(DEPOT,
                List.of(new RouteStop(A, 12_000), new RouteStop(B, 6_000)), TRUCK, true);

        // depot -> A: 18000 kg on board, 100 km x (28 + 7.5) / 100 = 35.5 L, driver (60 + 30) / 60 x 45 = 67.50
        Leg toA = route.stopLegs().get(0);
        assertLeg(toA, DEPOT, A, 18_000, 35.5, 213.00, 67.50, 60);
        assertThat(toA.serviceMinutes()).isEqualTo(30);
        assertThat(toA.costPln()).isCloseTo(280.50, EXACT);

        // A -> B: 6000 kg, 50 km x (28 + 2.5) / 100 = 15.25 L, driver (45 + 30) / 60 x 45 = 56.25,
        // arrives 60 + 30 + 45 = 135 min after departure
        Leg toB = route.stopLegs().get(1);
        assertLeg(toB, A, B, 6_000, 15.25, 91.50, 56.25, 135);

        // B -> depot: empty, 200 km x 28 / 100 = 56 L, driver 150 / 60 x 45 = 112.50, no service at the depot,
        // arrives 135 + 30 + 150 = 315 min after departure
        Leg back = route.returnLeg().orElseThrow();
        assertLeg(back, B, DEPOT, 0, 56.0, 336.00, 112.50, 315);
        assertThat(back.serviceMinutes()).isZero();

        Totals totals = route.totals();
        assertThat(totals.distanceKm()).isCloseTo(350, EXACT);
        assertThat(totals.drivingMinutes()).isCloseTo(255, EXACT);
        assertThat(totals.serviceMinutes()).isCloseTo(60, EXACT);
        assertThat(totals.totalMinutes()).isCloseTo(315, EXACT);
        assertThat(totals.litres()).isCloseTo(106.75, EXACT);
        assertThat(totals.fuelCostPln()).isCloseTo(640.50, EXACT);
        assertThat(totals.driverCostPln()).isCloseTo(236.25, EXACT);
        assertThat(totals.costPln()).isCloseTo(876.75, EXACT);
        assertThat(route.allLegs()).containsExactly(toA, toB, back);
    }

    @Test
    void withoutReturnTheRouteEndsAtTheLastStop() {
        RouteEvaluation route = evaluator.evaluate(DEPOT,
                List.of(new RouteStop(A, 12_000), new RouteStop(B, 6_000)), TRUCK, false);

        assertThat(route.returnLeg()).isEmpty();
        assertThat(route.allLegs()).hasSize(2);
        Totals totals = route.totals();
        assertThat(totals.distanceKm()).isCloseTo(150, EXACT);       // 100 + 50
        assertThat(totals.litres()).isCloseTo(50.75, EXACT);         // 35.5 + 15.25
        assertThat(totals.totalMinutes()).isCloseTo(165, EXACT);     // 60 + 45 driving + 2 x 30 service
        assertThat(totals.fuelCostPln()).isCloseTo(304.50, EXACT);   // 50.75 x 6
        assertThat(totals.driverCostPln()).isCloseTo(123.75, EXACT); // 165 / 60 x 45
        assertThat(totals.costPln()).isCloseTo(428.25, EXACT);
    }

    @Test
    void zeroWeightStopLeavesTheLoadUnchanged() {
        RouteEvaluation route = evaluator.evaluate(DEPOT,
                List.of(new RouteStop(A, 0), new RouteStop(B, 6_000)), TRUCK, false);

        // both legs carry 6000 kg: 100 km x (28 + 2.5) / 100 = 30.5 L, then 50 km x 30.5 / 100 = 15.25 L
        assertThat(route.stopLegs()).extracting(Leg::loadWeightKg).containsExactly(6_000.0, 6_000.0);
        assertThat(route.stopLegs().get(0).litres()).isCloseTo(30.5, EXACT);
        assertThat(route.stopLegs().get(1).litres()).isCloseTo(15.25, EXACT);
    }

    @Test
    void singleStopWithReturnHasOneStopLegAndAReturnLeg() {
        RouteEvaluation route = evaluator.evaluate(DEPOT, List.of(new RouteStop(A, 12_000)), TRUCK, true);

        assertThat(route.stopLegs()).hasSize(1);
        // depot -> A: 12000 kg, 33.0 L, driver (60 + 30) / 60 x 45 = 67.50
        assertLeg(route.stopLegs().get(0), DEPOT, A, 12_000, 33.0, 198.00, 67.50, 60);
        // A -> depot: empty, 28 L, driver 60 / 60 x 45 = 45.00, arrives 60 drive + 30 service + 60 drive = 150 min
        assertLeg(route.returnLeg().orElseThrow(), A, DEPOT, 0, 28.0, 168.00, 45.00, 150);
        assertThat(route.totals().totalMinutes()).isCloseTo(150, EXACT);
        assertThat(route.totals().costPln()).isCloseTo(478.50, EXACT); // 198 + 67.50 + 168 + 45
    }

    @Test
    void serviceTimeIsAddedPerStopAndShiftsLaterArrivals() {
        RouteEvaluator noService = new RouteEvaluator(matrix, new CostParameters(6.00, 0));
        List<RouteStop> stops = List.of(new RouteStop(A, 12_000), new RouteStop(B, 6_000));

        RouteEvaluation without = noService.evaluate(DEPOT, stops, TRUCK, true);
        RouteEvaluation with = evaluator.evaluate(DEPOT, stops, TRUCK, true);

        assertThat(without.stopLegs()).extracting(Leg::arrivalMinutes).containsExactly(60.0, 105.0);
        assertThat(without.returnLeg().orElseThrow().arrivalMinutes()).isCloseTo(255, EXACT);
        assertThat(with.stopLegs()).extracting(Leg::arrivalMinutes).containsExactly(60.0, 135.0);
        assertThat(with.returnLeg().orElseThrow().arrivalMinutes()).isCloseTo(315, EXACT);
        // 2 stops x 30 min at 45 PLN/h = 45 PLN more driver cost; fuel unchanged
        assertThat(with.totals().driverCostPln() - without.totals().driverCostPln()).isCloseTo(45, EXACT);
        assertThat(with.totals().litres()).isCloseTo(without.totals().litres(), EXACT);
    }

    @Test
    void emptyRouteCostsNothingEvenWithReturnToDepot() {
        RouteEvaluation route = evaluator.evaluate(DEPOT, List.of(), TRUCK, true);

        assertThat(route.stopLegs()).isEmpty();
        assertThat(route.returnLeg()).isEmpty();
        assertThat(route.allLegs()).isEmpty();
        assertThat(route.totals()).isEqualTo(Totals.ZERO);
    }

    @Test
    void deliveringTheHeavyDropFirstUsesLessFuelOnTheSameDistance() {
        DistanceMatrix triangle = symmetricMatrix(
                leg(DEPOT, A, 100, 80),
                leg(A, B, 100, 80),
                leg(B, DEPOT, 100, 80));
        RouteEvaluator onTriangle = new RouteEvaluator(triangle, PARAMETERS);
        RouteStop heavy = new RouteStop(A, 18_000);
        RouteStop light = new RouteStop(B, 2_000);

        RouteEvaluation heavyFirst = onTriangle.evaluate(DEPOT, List.of(heavy, light), TRUCK, true);
        RouteEvaluation lightFirst = onTriangle.evaluate(DEPOT, List.of(light, heavy), TRUCK, true);

        // heavy first: 20000 kg, then 2000 kg, then empty = 36.333 + 28.833 + 28 = 93.167 L
        // light first: 20000 kg, then 18000 kg, then empty = 36.333 + 35.5 + 28 = 99.833 L
        assertThat(heavyFirst.totals().litres()).isCloseTo(93.0 + 1.0 / 6, EXACT);
        assertThat(lightFirst.totals().litres()).isCloseTo(99.0 + 5.0 / 6, EXACT);
        assertThat(heavyFirst.totals().costPln()).isLessThan(lightFirst.totals().costPln());
        assertThat(heavyFirst.totals().distanceKm()).isEqualTo(lightFirst.totals().distanceKm());
    }

    @ParameterizedTest
    @CsvSource({"0, 1", "0, 6000", "6000, 12000", "12000, 18000", "18000, 24000"})
    void moreLoadUsesMoreFuel(int lighterKg, int heavierKg) {
        double lighter = firstLeg(evaluator, lighterKg, TRUCK).litres();
        double heavier = firstLeg(evaluator, heavierKg, TRUCK).litres();

        assertThat(heavier).isGreaterThan(lighter);
    }

    @Test
    void withoutExtraConsumptionLoadDoesNotMatter() {
        TruckSpec flat = new TruckSpec(8, 33, 24_000, 28, 0, 45);

        assertThat(firstLeg(evaluator, 0, flat).litres()).isCloseTo(28.0, EXACT);
        assertThat(firstLeg(evaluator, 24_000, flat).litres()).isCloseTo(28.0, EXACT);
    }

    @Test
    void valuesAreNotRoundedInTheSolver() {
        // 100 km x (28 + 10 x 2000 / 24000) / 100 = 28.8333... L; rounding happens only in DTO mapping
        Leg leg = firstLeg(evaluator, 2_000, TRUCK);

        assertThat(leg.litres()).isCloseTo(28.0 + 5.0 / 6, EXACT);
        assertThat(leg.litres()).isNotEqualTo(28.8);
    }

    @Test
    void legKeepsDistanceDurationAndSource() {
        DistanceMatrix estimated = symmetricMatrix(
                new LegEntry(DEPOT, A, new LegDistance(130, 111.4, DistanceSource.ESTIMATED)));

        Leg leg = firstLeg(new RouteEvaluator(estimated, PARAMETERS), 0, TRUCK);

        assertThat(leg.distance().isEstimated()).isTrue();
        assertThat(leg.distanceKm()).isEqualTo(130);
        assertThat(leg.drivingMinutes()).isEqualTo(111.4);
    }

    @Test
    void acceptsLoadExactlyAtPayload() {
        List<RouteStop> stops = List.of(new RouteStop(A, 20_000), new RouteStop(B, 4_000));

        assertThat(evaluator.evaluate(DEPOT, stops, TRUCK, true).stopLegs().get(0).loadWeightKg())
                .isEqualTo(24_000);
    }

    @Test
    void rejectsLoadAbovePayload() {
        List<RouteStop> stops = List.of(new RouteStop(A, 20_000), new RouteStop(B, 4_001));

        assertThatThrownBy(() -> evaluator.evaluate(DEPOT, stops, TRUCK, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("24001 kg exceeds payload 24000 kg");
    }

    @Test
    void rejectsTheDepotAsAStop() {
        assertThatThrownBy(() -> evaluator.evaluate(DEPOT, List.of(new RouteStop(DEPOT, 100)), TRUCK, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is the depot");
    }

    @Test
    void rejectsAWarehouseVisitedTwice() {
        List<RouteStop> stops = List.of(new RouteStop(A, 100), new RouteStop(A, 100));

        assertThatThrownBy(() -> evaluator.evaluate(DEPOT, stops, TRUCK, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("appears twice");
    }

    @Test
    void rejectsAWarehouseUnknownToTheDistanceProvider() {
        assertThatThrownBy(() -> evaluator.evaluate(DEPOT, List.of(new RouteStop(99, 100)), TRUCK, true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Warehouse 99 is not in the distance matrix");
    }

    private static Leg firstLeg(RouteEvaluator routeEvaluator, int dropKg, TruckSpec truck) {
        return routeEvaluator.evaluate(DEPOT, List.of(new RouteStop(A, dropKg)), truck, false).stopLegs().get(0);
    }

    private static void assertLeg(Leg leg, long fromId, long toId, double loadKg, double litres, double fuelCost,
            double driverCost, double arrivalMinutes) {
        assertThat(leg.fromId()).isEqualTo(fromId);
        assertThat(leg.toId()).isEqualTo(toId);
        assertThat(leg.loadWeightKg()).isCloseTo(loadKg, EXACT);
        assertThat(leg.litres()).isCloseTo(litres, EXACT);
        assertThat(leg.fuelCostPln()).isCloseTo(fuelCost, EXACT);
        assertThat(leg.driverCostPln()).isCloseTo(driverCost, EXACT);
        assertThat(leg.arrivalMinutes()).isCloseTo(arrivalMinutes, EXACT);
    }

    private record LegEntry(long fromId, long toId, LegDistance distance) {
    }

    private static LegEntry leg(long fromId, long toId, double km, double minutes) {
        return new LegEntry(fromId, toId, new LegDistance(km, minutes, DistanceSource.SEEDED));
    }

    /** Matrix holding each given leg in both directions. */
    private static DistanceMatrix symmetricMatrix(LegEntry... entries) {
        Map<DirectedPair, LegDistance> legs = new HashMap<>();
        for (LegEntry entry : entries) {
            legs.put(new DirectedPair(entry.fromId(), entry.toId()), entry.distance());
            legs.put(new DirectedPair(entry.toId(), entry.fromId()), entry.distance());
        }
        List<Long> ids = legs.keySet().stream().map(DirectedPair::fromId).distinct().sorted().toList();
        return new DistanceMatrix(ids, legs);
    }
}
