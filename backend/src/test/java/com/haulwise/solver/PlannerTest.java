package com.haulwise.solver;

import static com.haulwise.solver.TestMatrices.lineMatrix;
import static com.haulwise.solver.TestMatrices.matrix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.haulwise.solver.model.CostParameters;
import com.haulwise.solver.model.Delta;
import com.haulwise.solver.model.DirectedPair;
import com.haulwise.solver.model.DistanceMatrix;
import com.haulwise.solver.model.Leg;
import com.haulwise.solver.model.Mode;
import com.haulwise.solver.model.PlanVariant;
import com.haulwise.solver.model.Priority;
import com.haulwise.solver.model.PriorityWeights;
import com.haulwise.solver.model.RoutePlan;
import com.haulwise.solver.model.SequencedRoute;
import com.haulwise.solver.model.SequencingMethod;
import com.haulwise.solver.model.ShipmentItem;
import com.haulwise.solver.model.SolverInput;
import com.haulwise.solver.model.SolverResult;
import com.haulwise.solver.model.Stop;
import com.haulwise.solver.model.Totals;
import com.haulwise.solver.model.TruckSpec;
import com.haulwise.solver.model.Unassigned;
import com.haulwise.solver.model.UnassignedReason;
import com.haulwise.solver.model.WarningCode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * The whole pipeline (ARCHITECTURE 6.1). Hand-checkable fixtures as in {@link PriorityObjectiveTest}: trucks
 * burn 10 L/100 km whatever their load and cost nothing per hour, diesel costs 10 PLN/L, no service time, no
 * return leg - so a route's cost in PLN equals its length in km and minutes equal km. The depot is warehouse 1.
 * <p>
 * LINE: depot 1 at 0, 2 at +1, 3 at -1.5, 4 at +3, 5 at +5, 6 at +6 (10 km per unit). For stops 2, 3, 4:
 * 3, 2, 4 = 15 + 25 + 20 = 60 km is the cheapest order (arrival at 4 after 60 min); 2, 4, 3 = 10 + 20 + 45 =
 * 75 km reaches 4 after 30 min. With 4 URGENT at 150 PLN/h that is 60 + 150 = 210 vs 75 + 75 = 150, so the
 * PRIORITY order is 2, 4, 3 (4, 2, 3 ties at 150; the smaller id sequence wins).
 */
class PlannerTest {

    private static final Offset<Double> EXACT = within(1e-9);
    private static final long DEPOT = 1;
    private static final CostParameters KM_PARAMETERS = new CostParameters(10, 0);
    private static final PriorityWeights DEFAULT_WEIGHTS = new PriorityWeights(150, 50);
    private static final PriorityWeights NO_WEIGHTS = new PriorityWeights(0, 0);
    private static final DistanceMatrix LINE = lineMatrix(0, 1, -1.5, 3, 5, 6);
    private static final Map<Long, String> CITIES = Map.of(
            1L, "Warsaw", 2L, "Lodz", 3L, "Krakow", 4L, "Lublin", 5L, "Gdansk", 6L, "Szczecin");

    private final Planner planner = new Planner();

    @Test
    void priorityMovesTheLateUrgentStopEarlierAndReportsTheDelta() {
        SolverResult result = planner.plan(input(List.of(kmTruck(1)),
                List.of(normal(12, 2, 900), normal(13, 3, 800), urgent(14, 4, 1_000)), DEFAULT_WEIGHTS));

        assertThat(warehouseIds(result.optimal())).containsExactly(List.of(3L, 2L, 4L));
        assertThat(warehouseIds(result.priority())).containsExactly(List.of(2L, 4L, 3L));
        assertThat(result.optimal().routes().get(0).route().evaluation().stopLegs())
                .extracting(Leg::arrivalMinutes).containsExactly(15.0, 40.0, 60.0);
        assertThat(result.priority().routes().get(0).route().evaluation().stopLegs())
                .extracting(Leg::arrivalMinutes).containsExactly(10.0, 30.0, 75.0);
        assertThat(result.optimal().totals().costPln()).isCloseTo(60, EXACT);
        assertThat(result.priority().totals().costPln()).isCloseTo(75, EXACT);

        Delta delta = result.delta();
        assertThat(delta.distanceKm()).isCloseTo(15, EXACT);
        assertThat(delta.durationMinutes()).isCloseTo(15, EXACT);
        assertThat(delta.litres()).isCloseTo(1.5, EXACT);
        assertThat(delta.fuelCostPln()).isCloseTo(15, EXACT);
        assertThat(delta.driverCostPln()).isCloseTo(0, EXACT);
        assertThat(delta.costPln()).isCloseTo(15, EXACT);
        assertThat(delta.noExtraCost()).isFalse();
        assertThat(delta.sentence()).isEqualTo("Prioritising Lublin costs 15.00 PLN more (15 km and 15 min longer).");
    }

    @Test
    void bothVariantsShareTheSameAssignment() {
        SolverResult result = planner.plan(twoTruckInput(DEFAULT_WEIGHTS));

        assertThat(result.optimal().mode()).isEqualTo(Mode.OPTIMAL);
        assertThat(result.priority().mode()).isEqualTo(Mode.PRIORITY);
        assertThat(result.optimal().routes()).extracting(RoutePlan::load)
                .containsExactlyElementsOf(result.priority().routes().stream().map(RoutePlan::load).toList());
        assertThat(shipmentIdsPerTruck(result.optimal())).isEqualTo(shipmentIdsPerTruck(result.priority()))
                .containsExactly(Set.of(12L, 13L, 14L), Set.of(15L, 16L));
    }

    @Test
    void zeroWeightsGiveIdenticalVariantsAndNoExtraCost() {
        SolverResult result = planner.plan(input(List.of(kmTruck(1)),
                List.of(normal(12, 2, 900), normal(13, 3, 800), urgent(14, 4, 1_000)), NO_WEIGHTS));

        assertThat(warehouseIds(result.priority())).isEqualTo(warehouseIds(result.optimal()))
                .containsExactly(List.of(3L, 2L, 4L));
        assertThat(result.priority().totals()).isEqualTo(result.optimal().totals());
        Delta delta = result.delta();
        assertThat(delta.costPln()).isZero();
        assertThat(delta.distanceKm()).isZero();
        assertThat(delta.durationMinutes()).isZero();
        assertThat(delta.litres()).isZero();
        assertThat(delta.noExtraCost()).isTrue();
        assertThat(delta.sentence()).isEqualTo("Respecting priorities costs nothing extra.");
    }

    @Test
    void multipleTrucksAreSummed() {
        // Truck 1 carries 2, 3, 4 (the LINE case, +15 km); truck 2 carries 5, 6 (5, 6 = 50 + 10 km in both).
        SolverResult result = planner.plan(twoTruckInput(DEFAULT_WEIGHTS));

        assertThat(warehouseIds(result.optimal())).containsExactly(List.of(3L, 2L, 4L), List.of(5L, 6L));
        assertThat(warehouseIds(result.priority())).containsExactly(List.of(2L, 4L, 3L), List.of(5L, 6L));
        assertThat(result.optimal().totals().distanceKm()).isCloseTo(120, EXACT);
        assertThat(result.priority().totals().distanceKm()).isCloseTo(135, EXACT);
        assertThat(result.optimal().totals()).isEqualTo(sumOfRoutes(result.optimal()));
        assertThat(result.priority().totals()).isEqualTo(sumOfRoutes(result.priority()));
        assertThat(result.delta().costPln()).isCloseTo(15, EXACT);
        assertThat(result.delta().distanceKm()).isCloseTo(15, EXACT);
    }

    @Test
    void unassignedShipmentsPassThrough() {
        SolverResult result = planner.plan(twoTruckInput(DEFAULT_WEIGHTS));

        assertThat(result.unassigned()).containsExactly(new Unassigned(17, UnassignedReason.EXCEEDS_LARGEST_TRUCK,
                "4 pallets exceed the largest selected truck (3 pallets)."));
    }

    @Test
    void pinConflictWarningPassesThroughAndTheWinningPinIsDeliveredFirst() {
        // 12 (to 2) and 13 (to 3) are both pinned; 12 wins (heavier). PRIORITY: 2 first, then 4, 3 (75 km)
        // beats 3, 4 (80 km). OPTIMAL ignores pins: 3, 2, 4 (60 km).
        SolverResult result = planner.plan(input(List.of(kmTruck(1)),
                List.of(pinned(12, 2, 900), pinned(13, 3, 800), normal(14, 4, 1_000)), DEFAULT_WEIGHTS));

        assertThat(result.warnings()).singleElement().satisfies(warning -> {
            assertThat(warning.code()).isEqualTo(WarningCode.PIN_CONFLICT);
            assertThat(warning.truckId()).isEqualTo(1);
            assertThat(warning.shipmentIds()).containsExactly(13L);
        });
        assertThat(warehouseIds(result.optimal())).containsExactly(List.of(3L, 2L, 4L));
        assertThat(warehouseIds(result.priority())).containsExactly(List.of(2L, 4L, 3L));
        assertThat(result.delta().sentence())
                .isEqualTo("Delivering Lodz first costs 15.00 PLN more (15 km and 15 min longer).");
    }

    /**
     * D-14 with the heuristic (maxBruteForceStops = 0). Directed km: 1->2 10, 1->3 11, 1->4 50, 2->3 10,
     * 3->2 10, 2->4 100, 4->2 5, 3->4 10, 4->3 10, everything back to the depot 100; stop 3 pinned, all NORMAL.
     * <ul>
     *   <li>COST: nearest neighbour 2, 3, 4 = 30 km; its 2-opt moves 3, 2, 4 = 121, 4, 3, 2 = 70 and
     *       2, 4, 3 = 120 are all worse, so the heuristic stops at 30.</li>
     *   <li>PRIORITY: 3 fixed first, nearest neighbour 3, 2, 4 = 121 (2 and 4 tie at 10 km, lower id), then
     *       2-opt reverses 2, 4 into 3, 4, 2 = 11 + 10 + 5 = 26 km - cheaper than the COST result.</li>
     * </ul>
     */
    @Test
    void optimalTakesThePriorityOrderWhenItIsCheaper() {
        Map<DirectedPair, Double> km = new HashMap<>();
        for (long to = 2; to <= 4; to++) {
            km.put(new DirectedPair(to, DEPOT), 100.0);
        }
        km.put(new DirectedPair(1, 2), 10.0);
        km.put(new DirectedPair(1, 3), 11.0);
        km.put(new DirectedPair(1, 4), 50.0);
        km.put(new DirectedPair(2, 3), 10.0);
        km.put(new DirectedPair(3, 2), 10.0);
        km.put(new DirectedPair(2, 4), 100.0);
        km.put(new DirectedPair(4, 2), 5.0);
        km.put(new DirectedPair(3, 4), 10.0);
        km.put(new DirectedPair(4, 3), 10.0);
        DistanceMatrix trap = matrix(km);
        List<ShipmentItem> shipments = List.of(normal(12, 2, 900), pinned(13, 3, 800), normal(14, 4, 700));
        SolverInput input = new SolverInput(DEPOT, shipments, List.of(kmTruck(1)), false, trap, KM_PARAMETERS,
                DEFAULT_WEIGHTS, CITIES);

        SequencedRoute byCostAlone = new StopSequencer(trap, KM_PARAMETERS, 0).sequence(DEPOT,
                new PinConflictResolver(new StopGrouper()).resolve(
                        new BinPacker().pack(shipments, List.of(kmTruck(1))).loads().get(0)).stops(),
                kmTruck(1), false, RouteObjective.COST);
        SolverResult result = new Planner(0).plan(input);

        assertThat(byCostAlone.warehouseIds()).containsExactly(2L, 3L, 4L);
        assertThat(byCostAlone.evaluation().totals().costPln()).isCloseTo(30, EXACT);
        SequencedRoute optimal = result.optimal().routes().get(0).route();
        assertThat(optimal.warehouseIds()).containsExactly(3L, 4L, 2L);
        assertThat(optimal.method()).isEqualTo(SequencingMethod.NEAREST_NEIGHBOUR_TWO_OPT);
        assertThat(optimal.objectiveValue()).isCloseTo(26, EXACT);
        assertThat(warehouseIds(result.priority())).containsExactly(List.of(3L, 4L, 2L));
        assertThat(result.delta().costPln()).isZero();
        assertThat(result.delta().noExtraCost()).isTrue();
    }

    @Test
    void guardKeepsTheCostOrderOnEqualCost() {
        SequencedRoute byCost = route(List.of(normalStop(2)), 10);
        SequencedRoute byPriority = route(List.of(normalStop(3)), 10);

        assertThat(Planner.cheaper(byCost, byPriority)).isSameAs(byCost);
    }

    @Test
    void guardTakesAStrictlyCheaperPriorityOrderWithItsCostAsObjectiveValue() {
        SequencedRoute byCost = route(List.of(normalStop(2)), 10);
        SequencedRoute byPriority = new SequencedRoute(List.of(normalStop(3)),
                route(List.of(normalStop(3)), 9.999).evaluation(), 500, SequencingMethod.NEAREST_NEIGHBOUR_TWO_OPT);

        SequencedRoute chosen = Planner.cheaper(byCost, byPriority);

        assertThat(chosen.stops()).isEqualTo(byPriority.stops());
        assertThat(chosen.evaluation()).isEqualTo(byPriority.evaluation());
        assertThat(chosen.objectiveValue()).isEqualTo(9.999);
        assertThat(chosen.method()).isEqualTo(SequencingMethod.NEAREST_NEIGHBOUR_TWO_OPT);
    }

    @Test
    void noTrucksMeansEverythingUnassignedAndNothingExtra() {
        SolverResult result = planner.plan(input(List.of(), List.of(normal(12, 2, 900)), DEFAULT_WEIGHTS));

        assertThat(result.optimal().routes()).isEmpty();
        assertThat(result.priority().routes()).isEmpty();
        assertThat(result.optimal().totals()).isEqualTo(Totals.ZERO);
        assertThat(result.unassigned()).extracting(Unassigned::reason)
                .containsExactly(UnassignedReason.EXCEEDS_LARGEST_TRUCK);
        assertThat(result.delta().sentence()).isEqualTo("Respecting priorities costs nothing extra.");
    }

    @Test
    void sameInputGivesEqualResults() {
        assertThat(planner.plan(twoTruckInput(DEFAULT_WEIGHTS))).isEqualTo(planner.plan(twoTruckInput(DEFAULT_WEIGHTS)));
    }

    @Test
    void rejectsAnOutOfRangeBruteForceLimit() {
        assertThatThrownBy(() -> new Planner(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Planner(StopSequencer.MAX_BRUTE_FORCE_STOPS_LIMIT + 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxBruteForceStops must be 0..9");
    }

    @Test
    void inputRequiresACityForEveryDestination() {
        assertThatThrownBy(() -> new SolverInput(DEPOT, List.of(normal(12, 2, 900)), List.of(kmTruck(1)), false,
                LINE, KM_PARAMETERS, DEFAULT_WEIGHTS, Map.of(3L, "Krakow")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[2]");
    }

    @Test
    void shipmentToTheDepotIsRejected() {
        assertThatThrownBy(() -> planner.plan(input(List.of(kmTruck(1)), List.of(normal(12, DEPOT, 900)),
                DEFAULT_WEIGHTS))).isInstanceOf(IllegalArgumentException.class);
    }

    // --- fixtures -------------------------------------------------------------------------------------------

    /**
     * Two 3-pallet trucks, 1-pallet shipments: packing order 14 (URGENT), 12, 13 fills truck 1, then 15, 16
     * go to truck 2; 17 (4 pallets) fits no truck.
     */
    private static SolverInput twoTruckInput(PriorityWeights weights) {
        List<ShipmentItem> shipments = List.of(normal(12, 2, 900), normal(13, 3, 800), urgent(14, 4, 1_000),
                normal(15, 5, 700), normal(16, 6, 600),
                new ShipmentItem(17, 5, 4, 500, Priority.NORMAL, false, null));
        return input(List.of(smallTruck(1), smallTruck(2)), shipments, weights);
    }

    private static SolverInput input(List<TruckSpec> trucks, List<ShipmentItem> shipments, PriorityWeights weights) {
        return new SolverInput(DEPOT, shipments, trucks, false, LINE, KM_PARAMETERS, weights, CITIES);
    }

    private static TruckSpec kmTruck(long id) {
        return new TruckSpec(id, 33, 24_000, 10, 0, 0);
    }

    private static TruckSpec smallTruck(long id) {
        return new TruckSpec(id, 3, 24_000, 10, 0, 0);
    }

    private static ShipmentItem normal(long id, long destination, int weightKg) {
        return new ShipmentItem(id, destination, 1, weightKg, Priority.NORMAL, false, null);
    }

    private static ShipmentItem urgent(long id, long destination, int weightKg) {
        return new ShipmentItem(id, destination, 1, weightKg, Priority.URGENT, false, null);
    }

    private static ShipmentItem pinned(long id, long destination, int weightKg) {
        return new ShipmentItem(id, destination, 1, weightKg, Priority.NORMAL, true, null);
    }

    private static Stop normalStop(long warehouseId) {
        return new Stop(warehouseId, List.of(warehouseId), 1, 100, Priority.NORMAL, false, null);
    }

    private static SequencedRoute route(List<Stop> stops, double km) {
        Map<DirectedPair, Double> legs = new HashMap<>();
        for (long from = 1; from <= 3; from++) {
            for (long to = 1; to <= 3; to++) {
                if (from != to) {
                    legs.put(new DirectedPair(from, to), km);
                }
            }
        }
        RouteEvaluator evaluator = new RouteEvaluator(matrix(legs), KM_PARAMETERS);
        return new SequencedRoute(stops, evaluator.evaluate(DEPOT, stops.stream().map(Stop::toRouteStop).toList(),
                kmTruck(1), false), km, SequencingMethod.BRUTE_FORCE);
    }

    private static List<List<Long>> warehouseIds(PlanVariant variant) {
        return variant.routes().stream().map(route -> route.route().warehouseIds()).toList();
    }

    private static List<Set<Long>> shipmentIdsPerTruck(PlanVariant variant) {
        return variant.routes().stream()
                .map(route -> route.route().stops().stream()
                        .flatMap(stop -> stop.shipmentIds().stream())
                        .collect(Collectors.toSet()))
                .toList();
    }

    private static Totals sumOfRoutes(PlanVariant variant) {
        Totals sum = Totals.ZERO;
        for (RoutePlan route : variant.routes()) {
            sum = sum.plus(route.totals());
        }
        return sum;
    }
}
