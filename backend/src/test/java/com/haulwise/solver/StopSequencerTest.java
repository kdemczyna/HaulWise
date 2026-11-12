package com.haulwise.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.haulwise.solver.model.CostParameters;
import com.haulwise.solver.model.DirectedPair;
import com.haulwise.solver.model.DistanceMatrix;
import com.haulwise.solver.model.DistanceSource;
import com.haulwise.solver.model.LegDistance;
import com.haulwise.solver.model.Priority;
import com.haulwise.solver.model.RouteEvaluation;
import com.haulwise.solver.model.SequencedRoute;
import com.haulwise.solver.model.SequencingMethod;
import com.haulwise.solver.model.Stop;
import com.haulwise.solver.model.Totals;
import com.haulwise.solver.model.TruckSpec;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.LongStream;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * Hand-checkable fixtures. Unless a test says otherwise the truck burns 10 L/100 km whatever its load and
 * costs nothing per hour, diesel costs 10 PLN/L and there is no service time, so a route's cost in PLN equals
 * its length in km. The depot is warehouse 1; leg durations in minutes equal the km.
 */
class StopSequencerTest {

    private static final Offset<Double> EXACT = within(1e-9);
    private static final long DEPOT = 1;
    private static final TruckSpec KM_TRUCK = new TruckSpec(1, 33, 24_000, 10, 0, 0);
    private static final CostParameters KM_PARAMETERS = new CostParameters(10, 0);

    /**
     * Warehouses on a line, 10 km per unit: depot 1 at 0, 2 at +1, 3 at -1.5, 4 at +3.
     * Nearest neighbour goes 2, 4, 3 = 10 + 20 + 45 = 75 km; the optimum (no return) is 3, 2, 4 = 15 + 25 + 20 = 60 km.
     */
    private static final DistanceMatrix LINE = lineMatrix(0, 1, -1.5, 3);

    @Test
    void noStopsGiveAnEmptyRouteAtZeroCost() {
        SequencedRoute route = new StopSequencer(LINE, KM_PARAMETERS).sequence(DEPOT, List.of(), KM_TRUCK, true,
                RouteObjective.COST);

        assertThat(route.stops()).isEmpty();
        assertThat(route.evaluation().totals()).isEqualTo(Totals.ZERO);
        assertThat(route.evaluation().returnLeg()).isEmpty();
        assertThat(route.objectiveValue()).isZero();
    }

    @Test
    void oneStopIsDrivenThereAndBack() {
        SequencedRoute route = sequence(LINE, List.of(stop(4)), true);

        assertThat(route.warehouseIds()).containsExactly(4L);
        assertThat(route.objectiveValue()).isCloseTo(60, EXACT); // 30 km out, 30 km back
    }

    @Test
    void twoStopsWithoutAndWithReturnPickDifferentOrders() {
        // Asymmetric: 1->2 10, 2->3 10, 1->3 30, 3->2 10, 3->1 100, 2->1 10
        DistanceMatrix matrix = matrix(Map.of(
                pair(1, 2), 10.0, pair(2, 1), 10.0,
                pair(1, 3), 30.0, pair(3, 1), 100.0,
                pair(2, 3), 10.0, pair(3, 2), 10.0));
        List<Stop> stops = List.of(stop(2), stop(3));

        SequencedRoute oneWay = sequence(matrix, stops, false);
        SequencedRoute roundTrip = sequence(matrix, stops, true);

        assertThat(oneWay.warehouseIds()).containsExactly(2L, 3L);    // 20 km (vs 3, 2 = 40 km)
        assertThat(oneWay.objectiveValue()).isCloseTo(20, EXACT);
        assertThat(roundTrip.warehouseIds()).containsExactly(3L, 2L); // 30 + 10 + 10 = 50 km (vs 2, 3 = 120 km)
        assertThat(roundTrip.objectiveValue()).isCloseTo(50, EXACT);
    }

    @Test
    void bruteForceFindsTheOptimumWhereNearestNeighbourIsWrong() {
        StopSequencer sequencer = new StopSequencer(LINE, KM_PARAMETERS);
        List<Stop> stops = List.of(stop(2), stop(3), stop(4));

        SequencedRoute route = sequencer.sequence(DEPOT, stops, KM_TRUCK, false, RouteObjective.COST);

        assertThat(route.method()).isEqualTo(SequencingMethod.BRUTE_FORCE);
        assertThat(route.warehouseIds()).containsExactly(3L, 2L, 4L);
        assertThat(route.objectiveValue()).isCloseTo(60, EXACT);
        assertThat(route.evaluation().totals().costPln()).isCloseTo(60, EXACT);
        assertThat(sequencer.nearestNeighbour(DEPOT, stops, 0)).extracting(Stop::warehouseId)
                .containsExactly(2L, 4L, 3L);
    }

    @Test
    void chosenRouteCarriesTheRouteEvaluatorBreakdown() {
        List<Stop> stops = List.of(stop(2, 1_000), stop(3, 5_000), stop(4, 2_000));
        TruckSpec truck = new TruckSpec(1, 33, 24_000, 28, 10, 45);
        CostParameters parameters = new CostParameters(6.2, 30);

        SequencedRoute route = new StopSequencer(LINE, parameters).sequence(DEPOT, stops, truck, true,
                RouteObjective.COST);

        RouteEvaluation expected = new RouteEvaluator(LINE, parameters).evaluate(DEPOT,
                route.stops().stream().map(Stop::toRouteStop).toList(), truck, true);
        assertThat(route.evaluation()).isEqualTo(expected);
        assertThat(route.objectiveValue()).isEqualTo(expected.totals().costPln());
    }

    @Test
    void heavierDropIsDeliveredFirstWhenDistancesTie() {
        // Equilateral triangle, 100 km sides, with return: both orders drive 300 km, so only fuel differs.
        DistanceMatrix triangle = matrix(Map.of(
                pair(1, 2), 100.0, pair(2, 1), 100.0,
                pair(1, 3), 100.0, pair(3, 1), 100.0,
                pair(2, 3), 100.0, pair(3, 2), 100.0));
        TruckSpec truck = new TruckSpec(1, 33, 24_000, 28, 10, 45);

        SequencedRoute route = new StopSequencer(triangle, KM_PARAMETERS).sequence(DEPOT,
                List.of(stop(2, 1_000), stop(3, 20_000)), truck, true, RouteObjective.COST);

        assertThat(route.warehouseIds()).containsExactly(3L, 2L);
    }

    @Test
    void equalCostOrdersAreBrokenByTheSmallerWarehouseIdSequence() {
        // Symmetric round trip: 2, 3 and 3, 2 both cost 10 + 15 + 20 = 45
        DistanceMatrix matrix = matrix(Map.of(
                pair(1, 2), 10.0, pair(2, 1), 10.0,
                pair(1, 3), 20.0, pair(3, 1), 20.0,
                pair(2, 3), 15.0, pair(3, 2), 15.0));

        assertThat(sequence(matrix, List.of(stop(2), stop(3)), true).warehouseIds()).containsExactly(2L, 3L);
        assertThat(sequence(matrix, List.of(stop(3), stop(2)), true).warehouseIds()).containsExactly(2L, 3L);
        assertThat(heuristic(matrix).sequence(DEPOT, List.of(stop(3), stop(2)), KM_TRUCK, true,
                RouteObjective.COST).warehouseIds()).containsExactly(2L, 3L);
    }

    @Test
    void zeroLengthLegsBetweenCoLocatedWarehousesAreHandled() {
        // 2 and 3 share coordinates (0 km / 0 min ESTIMATED leg), both 50 km from the depot
        DistanceMatrix matrix = matrix(Map.of(
                pair(1, 2), 50.0, pair(2, 1), 50.0,
                pair(1, 3), 50.0, pair(3, 1), 50.0,
                pair(2, 3), 0.0, pair(3, 2), 0.0));
        List<Stop> stops = List.of(stop(3), stop(2));

        for (StopSequencer sequencer : List.of(new StopSequencer(matrix, KM_PARAMETERS), heuristic(matrix))) {
            SequencedRoute oneWay = sequencer.sequence(DEPOT, stops, KM_TRUCK, false, RouteObjective.COST);
            SequencedRoute roundTrip = sequencer.sequence(DEPOT, stops, KM_TRUCK, true, RouteObjective.COST);

            assertThat(oneWay.warehouseIds()).containsExactly(2L, 3L);
            assertThat(oneWay.objectiveValue()).isCloseTo(50, EXACT);
            assertThat(oneWay.evaluation().stopLegs().get(1).distanceKm()).isZero();
            assertThat(roundTrip.warehouseIds()).containsExactly(2L, 3L);
            assertThat(roundTrip.objectiveValue()).isCloseTo(100, EXACT);
        }
    }

    @Test
    void pluggableObjectiveChangesTheChosenOrder() {
        // Minimise the arrival time at warehouse 4: 4, 2, 3 / 4, 3, 2 / 2, 4, 3 all reach it after 30 min
        // (2 lies on the way), the smallest id sequence wins -> 2, 4, 3
        RouteObjective reachFourEarly = (stops, evaluation) ->
                evaluation.stopLegs().get(indexOf(stops, 4)).arrivalMinutes();

        SequencedRoute byCost = sequence(LINE, List.of(stop(2), stop(3), stop(4)), false);
        SequencedRoute byArrival = new StopSequencer(LINE, KM_PARAMETERS).sequence(DEPOT,
                List.of(stop(2), stop(3), stop(4)), KM_TRUCK, false, reachFourEarly);

        assertThat(byCost.warehouseIds()).containsExactly(3L, 2L, 4L);
        assertThat(byArrival.warehouseIds()).containsExactly(2L, 4L, 3L);
        assertThat(byArrival.objectiveValue()).isCloseTo(30, EXACT);
        assertThat(byArrival.evaluation().totals().costPln()).isCloseTo(75, EXACT); // reported cost stays pure cost
    }

    @Test
    void objectiveThatPinsFixesThePinnedStopFirstAndSearchesTheRest() {
        List<Stop> stops = List.of(stop(2), stop(3), pinnedStop(4));

        for (StopSequencer sequencer : List.of(new StopSequencer(LINE, KM_PARAMETERS), heuristic(LINE))) {
            SequencedRoute pinned = sequencer.sequence(DEPOT, stops, KM_TRUCK, false, costWithPin());
            SequencedRoute ignored = sequencer.sequence(DEPOT, stops, KM_TRUCK, false, RouteObjective.COST);

            assertThat(pinned.warehouseIds()).containsExactly(4L, 2L, 3L); // 30 + 20 + 25 = 75 (vs 4, 3, 2 = 100)
            assertThat(pinned.objectiveValue()).isCloseTo(75, EXACT);
            assertThat(ignored.warehouseIds()).first().isNotEqualTo(4L);
        }
    }

    @Test
    void objectiveThatPinsRejectsMoreThanOnePinnedStop() {
        List<Stop> stops = List.of(pinnedStop(2), pinnedStop(3));

        assertThatThrownBy(() -> sequenceWith(LINE, stops, costWithPin()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("found 2");
        assertThat(sequenceWith(LINE, stops, RouteObjective.COST).stops()).hasSize(2);
    }

    @Test
    void heuristicIsUsedAboveTheThresholdAndNeverWorseThanNearestNeighbour() {
        DistanceMatrix plane = randomPlaneMatrix(10, 7);
        List<Stop> stops = stopsUpTo(10);
        StopSequencer sequencer = new StopSequencer(plane, KM_PARAMETERS);

        SequencedRoute route = sequencer.sequence(DEPOT, stops, KM_TRUCK, true, RouteObjective.COST);
        double nearestNeighbourCost = new RouteEvaluator(plane, KM_PARAMETERS).evaluate(DEPOT,
                sequencer.nearestNeighbour(DEPOT, stops, 0).stream().map(Stop::toRouteStop).toList(), KM_TRUCK, true)
                .totals().costPln();

        assertThat(route.method()).isEqualTo(SequencingMethod.NEAREST_NEIGHBOUR_TWO_OPT);
        assertThat(route.warehouseIds()).containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(2, 10).boxed()
                .toList());
        assertThat(route.objectiveValue()).isLessThanOrEqualTo(nearestNeighbourCost + 1e-9);
    }

    @Test
    void eightStopsAreSolvedByBruteForceToTheExhaustiveOptimum() {
        // Load-dependent fuel, driver and service cost, so the optimum is not just the shortest tour
        DistanceMatrix plane = randomPlaneMatrix(9, 11);
        TruckSpec truck = new TruckSpec(1, 33, 24_000, 28, 10, 45);
        CostParameters parameters = new CostParameters(6.2, 30);
        List<Stop> stops = LongStream.rangeClosed(2, 9)
                .mapToObj(id -> stop(id, (int) (id * 500))).toList(); // 1000..4500 kg, 22 000 kg in total

        SequencedRoute route = new StopSequencer(plane, parameters).sequence(DEPOT, stops, truck, true,
                RouteObjective.COST);

        assertThat(route.method()).isEqualTo(SequencingMethod.BRUTE_FORCE);
        assertThat(route.objectiveValue())
                .isCloseTo(exhaustiveMinimumCost(plane, parameters, truck, stops, true), within(1e-6));
    }

    @Test
    void bruteForceScoresEveryOrderExactlyOnce() {
        DistanceMatrix plane = randomPlaneMatrix(6, 13);
        List<Stop> free = stopsUpTo(6);
        List<Stop> withPin = List.of(stop(2), stop(3), pinnedStop(4), stop(5), stop(6));

        CountingObjective unpinned = new CountingObjective(false);
        new StopSequencer(plane, KM_PARAMETERS).sequence(DEPOT, free, KM_TRUCK, false, unpinned);
        CountingObjective pinned = new CountingObjective(true);
        new StopSequencer(plane, KM_PARAMETERS).sequence(DEPOT, withPin, KM_TRUCK, false, pinned);

        assertThat(unpinned.calls).isEqualTo(120);           // 5!
        assertThat(unpinned.distinctOrders).hasSize(120);
        assertThat(pinned.calls).isEqualTo(24);              // 4! behind the pinned head
        assertThat(pinned.distinctOrders).hasSize(24)
                .allSatisfy(order -> assertThat(order.get(0)).isEqualTo(4L));
    }

    @Test
    void heuristicOnAnAsymmetricMatrixLiesBetweenTheOptimumAndNearestNeighbour() {
        DistanceMatrix matrix = randomAsymmetricMatrix(9, 17);
        List<Stop> stops = stopsUpTo(9);
        StopSequencer heuristic = heuristic(matrix);

        SequencedRoute route = heuristic.sequence(DEPOT, stops, KM_TRUCK, true, RouteObjective.COST);
        double nearestNeighbourCost = new RouteEvaluator(matrix, KM_PARAMETERS).evaluate(DEPOT,
                heuristic.nearestNeighbour(DEPOT, stops, 0).stream().map(Stop::toRouteStop).toList(), KM_TRUCK, true)
                .totals().costPln();
        double optimum = exhaustiveMinimumCost(matrix, KM_PARAMETERS, KM_TRUCK, stops, true);

        assertThat(route.method()).isEqualTo(SequencingMethod.NEAREST_NEIGHBOUR_TWO_OPT);
        assertThat(route.warehouseIds()).containsExactlyInAnyOrderElementsOf(LongStream.rangeClosed(2, 9).boxed()
                .toList());
        assertThat(route.objectiveValue()).isLessThanOrEqualTo(nearestNeighbourCost + 1e-9)
                .isGreaterThanOrEqualTo(optimum - 1e-9);
    }

    @Test
    void resultDoesNotDependOnTheInputOrder() {
        DistanceMatrix plane = randomPlaneMatrix(12, 3);
        List<Stop> stops = stopsUpTo(12);
        List<Stop> shuffled = new ArrayList<>(stops);
        Collections.shuffle(shuffled, new Random(5));
        StopSequencer sequencer = new StopSequencer(plane, KM_PARAMETERS);

        assertThat(sequencer.sequence(DEPOT, shuffled, KM_TRUCK, false, RouteObjective.COST))
                .isEqualTo(sequencer.sequence(DEPOT, stops, KM_TRUCK, false, RouteObjective.COST));
        List<Stop> six = new ArrayList<>(shuffled.subList(0, 6));
        List<Stop> sixReversed = new ArrayList<>(six);
        Collections.reverse(sixReversed);
        assertThat(sequencer.sequence(DEPOT, six, KM_TRUCK, false, RouteObjective.COST))
                .isEqualTo(sequencer.sequence(DEPOT, sixReversed, KM_TRUCK, false, RouteObjective.COST));
    }

    @Test
    void depotAsAStopIsRejected() {
        assertThatThrownBy(() -> sequence(LINE, List.of(stop(DEPOT), stop(2)), false))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("is the depot");
    }

    @Test
    void bruteForceThresholdMustStayWithinTheLimit() {
        assertThatThrownBy(() -> new StopSequencer(LINE, KM_PARAMETERS, 10))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StopSequencer(LINE, KM_PARAMETERS, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static SequencedRoute sequence(DistanceMatrix matrix, List<Stop> stops, boolean returnToDepot) {
        return new StopSequencer(matrix, KM_PARAMETERS).sequence(DEPOT, stops, KM_TRUCK, returnToDepot,
                RouteObjective.COST);
    }

    private static SequencedRoute sequenceWith(DistanceMatrix matrix, List<Stop> stops, RouteObjective objective) {
        return new StopSequencer(matrix, KM_PARAMETERS).sequence(DEPOT, stops, KM_TRUCK, false, objective);
    }

    /** Always takes the nearest neighbour + 2-opt path. */
    private static StopSequencer heuristic(DistanceMatrix matrix) {
        return new StopSequencer(matrix, KM_PARAMETERS, 0);
    }

    /** Cost objective that fixes the pinned stop first (stand-in for the T-11 priority objective). */
    private static RouteObjective costWithPin() {
        return new RouteObjective() {
            @Override
            public double score(List<Stop> stops, RouteEvaluation evaluation) {
                return evaluation.totals().costPln();
            }

            @Override
            public boolean pinnedStopFirst() {
                return true;
            }
        };
    }

    /** Cost objective that records every order it is asked to score. */
    private static final class CountingObjective implements RouteObjective {

        private final boolean pinnedStopFirst;
        private final Set<List<Long>> distinctOrders = new HashSet<>();
        private int calls;

        CountingObjective(boolean pinnedStopFirst) {
            this.pinnedStopFirst = pinnedStopFirst;
        }

        @Override
        public double score(List<Stop> stops, RouteEvaluation evaluation) {
            calls++;
            distinctOrders.add(stops.stream().map(Stop::warehouseId).toList());
            return evaluation.totals().costPln();
        }

        @Override
        public boolean pinnedStopFirst() {
            return pinnedStopFirst;
        }
    }

    /** Independent reference: the lowest cost over all orders, by plain recursion over RouteEvaluator. */
    private static double exhaustiveMinimumCost(DistanceMatrix matrix, CostParameters parameters, TruckSpec truck,
            List<Stop> stops, boolean returnToDepot) {
        RouteEvaluator evaluator = new RouteEvaluator(matrix, parameters);
        double[] best = {Double.POSITIVE_INFINITY};
        exhaust(new ArrayList<>(), new ArrayList<>(stops), order -> {
            double cost = evaluator.evaluate(DEPOT, order.stream().map(Stop::toRouteStop).toList(), truck,
                    returnToDepot).totals().costPln();
            best[0] = Math.min(best[0], cost);
        });
        return best[0];
    }

    private static void exhaust(List<Stop> prefix, List<Stop> remaining, Consumer<List<Stop>> visitor) {
        if (remaining.isEmpty()) {
            visitor.accept(prefix);
            return;
        }
        for (Stop next : List.copyOf(remaining)) {
            prefix.add(next);
            remaining.remove(next);
            exhaust(prefix, remaining, visitor);
            remaining.add(next);
            prefix.remove(prefix.size() - 1);
        }
    }

    private static int indexOf(List<Stop> stops, long warehouseId) {
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i).warehouseId() == warehouseId) {
                return i;
            }
        }
        throw new IllegalArgumentException("No stop at " + warehouseId);
    }

    private static Stop stop(long warehouseId) {
        return stop(warehouseId, 1_000);
    }

    private static Stop stop(long warehouseId, int dropWeightKg) {
        return new Stop(warehouseId, List.of(warehouseId * 100), 1, dropWeightKg, Priority.NORMAL, false, null);
    }

    private static Stop pinnedStop(long warehouseId) {
        return new Stop(warehouseId, List.of(warehouseId * 100), 1, 1_000, Priority.NORMAL, true, null);
    }

    /** Stops at warehouses 2..lastId. */
    private static List<Stop> stopsUpTo(long lastId) {
        return LongStream.rangeClosed(2, lastId).mapToObj(StopSequencerTest::stop).toList();
    }

    private static DirectedPair pair(long from, long to) {
        return new DirectedPair(from, to);
    }

    /** Warehouse i + 1 at position x[i] on a line, 10 km per unit; minutes = km. */
    private static DistanceMatrix lineMatrix(double... x) {
        Map<DirectedPair, Double> km = new HashMap<>();
        for (int i = 0; i < x.length; i++) {
            for (int j = 0; j < x.length; j++) {
                if (i != j) {
                    km.put(pair(i + 1, j + 1), 10 * Math.abs(x[i] - x[j]));
                }
            }
        }
        return matrix(km);
    }

    /** Warehouses 1..count at seeded random points in a 500 x 500 km square; Euclidean km, minutes = km. */
    private static DistanceMatrix randomPlaneMatrix(int count, long seed) {
        Random random = new Random(seed);
        double[][] points = new double[count][];
        for (int i = 0; i < count; i++) {
            points[i] = new double[] {random.nextDouble() * 500, random.nextDouble() * 500};
        }
        Map<DirectedPair, Double> km = new HashMap<>();
        for (int i = 0; i < count; i++) {
            for (int j = 0; j < count; j++) {
                if (i != j) {
                    km.put(pair(i + 1, j + 1), Math.hypot(points[i][0] - points[j][0], points[i][1] - points[j][1]));
                }
            }
        }
        return matrix(km);
    }

    /** Warehouses 1..count with an independent seeded random 10..500 km for every directed pair; minutes = km. */
    private static DistanceMatrix randomAsymmetricMatrix(int count, long seed) {
        Random random = new Random(seed);
        Map<DirectedPair, Double> km = new HashMap<>();
        for (int i = 1; i <= count; i++) {
            for (int j = 1; j <= count; j++) {
                if (i != j) {
                    km.put(pair(i, j), 10 + random.nextDouble() * 490);
                }
            }
        }
        return matrix(km);
    }

    private static DistanceMatrix matrix(Map<DirectedPair, Double> km) {
        Map<DirectedPair, LegDistance> legs = new HashMap<>();
        km.forEach((pair, distance) -> legs.put(pair, new LegDistance(distance, distance, DistanceSource.ESTIMATED)));
        List<Long> ids = km.keySet().stream().map(DirectedPair::fromId).distinct().sorted().toList();
        return new DistanceMatrix(ids, legs);
    }
}
