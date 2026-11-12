package com.haulwise.solver;

import static com.haulwise.solver.TestMatrices.lineMatrix;
import static com.haulwise.solver.TestMatrices.matrix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.haulwise.solver.model.CostParameters;
import com.haulwise.solver.model.DirectedPair;
import com.haulwise.solver.model.DistanceMatrix;
import com.haulwise.solver.model.Priority;
import com.haulwise.solver.model.PriorityWeights;
import com.haulwise.solver.model.RouteEvaluation;
import com.haulwise.solver.model.SequencedRoute;
import com.haulwise.solver.model.Stop;
import com.haulwise.solver.model.TruckSpec;
import java.util.List;
import java.util.Map;
import org.assertj.core.data.Offset;
import org.junit.jupiter.api.Test;

/**
 * Hand-checkable fixtures (same conventions as {@link StopSequencerTest}): the truck burns 10 L/100 km whatever
 * its load and costs nothing per hour, diesel costs 10 PLN/L and there is no service time unless a test says so,
 * so a route's cost in PLN equals its length in km and leg minutes equal km. The depot is warehouse 1.
 * <p>
 * LINE: warehouses on a line, 10 km per unit - depot 1 at 0, 2 at +1, 3 at -1.5, 4 at +3. Without return:
 * 3, 2, 4 = 15 + 25 + 20 = 60 km (the cheapest), arrival at 4 after 60 min; 2, 4, 3 = 10 + 20 + 45 = 75 km,
 * arrival at 4 after 30 min.
 */
class PriorityObjectiveTest {

    private static final Offset<Double> EXACT = within(1e-9);
    private static final long DEPOT = 1;
    private static final TruckSpec KM_TRUCK = new TruckSpec(1, 33, 24_000, 10, 0, 0);
    private static final CostParameters KM_PARAMETERS = new CostParameters(10, 0);
    private static final PriorityWeights DEFAULT_WEIGHTS = new PriorityWeights(150, 50);
    private static final DistanceMatrix LINE = lineMatrix(0, 1, -1.5, 3);

    @Test
    void penaltyIsWeightTimesArrivalHoursIncludingEarlierServiceButNotTheStopsOwn() {
        // Order 2 (HIGH), 4 (URGENT), 3 (NORMAL), 30 min service, no return, weights URGENT 120 / HIGH 60.
        // Arrivals: 2 at 10; 4 at 10 + 30 + 20 = 60; 3 at 60 + 30 + 45 = 135 min.
        // Penalty = 60 x 10/60 + 120 x 60/60 + 0 x 135/60 = 10 + 120 = 130 PLN.
        // Cost = 75 km x 10 L/100 km x 10 PLN/L = 75 PLN (driver costs 0) -> score 205.
        List<Stop> order = List.of(stop(2, Priority.HIGH), stop(4, Priority.URGENT), stop(3, Priority.NORMAL));
        RouteEvaluation evaluation = new RouteEvaluator(LINE, new CostParameters(10, 30)).evaluate(DEPOT,
                order.stream().map(Stop::toRouteStop).toList(), KM_TRUCK, false);
        PriorityObjective objective = new PriorityObjective(new PriorityWeights(120, 60));

        assertThat(evaluation.stopLegs()).extracting(leg -> leg.arrivalMinutes()).containsExactly(10.0, 60.0, 135.0);
        assertThat(evaluation.totals().costPln()).isCloseTo(75, EXACT);
        assertThat(objective.penaltyPln(order, evaluation)).isCloseTo(130, EXACT);
        assertThat(objective.score(order, evaluation)).isCloseTo(205, EXACT);
    }

    @Test
    void urgentStopMovesEarlierWhenThePenaltyOutweighsTheExtraCost() {
        // URGENT at 4, 150 PLN/h: 3, 2, 4 scores 60 + 150 x 1 h = 210; 2, 4, 3 scores 75 + 150 x 0.5 h = 150
        // (4, 2, 3 ties at 150, the smaller id sequence wins).
        List<Stop> stops = List.of(stop(2, Priority.NORMAL), stop(3, Priority.NORMAL), stop(4, Priority.URGENT));

        SequencedRoute byCost = sequence(stops, RouteObjective.COST);
        SequencedRoute byPriority = sequence(stops, new PriorityObjective(DEFAULT_WEIGHTS));

        assertThat(byCost.warehouseIds()).containsExactly(3L, 2L, 4L);
        assertThat(byPriority.warehouseIds()).containsExactly(2L, 4L, 3L);
        assertThat(byPriority.objectiveValue()).isCloseTo(150, EXACT);
    }

    @Test
    void reportedCostStaysUnpenalised() {
        List<Stop> stops = List.of(stop(2, Priority.NORMAL), stop(3, Priority.NORMAL), stop(4, Priority.URGENT));
        PriorityObjective objective = new PriorityObjective(DEFAULT_WEIGHTS);

        SequencedRoute route = sequence(stops, objective);

        RouteEvaluation pureCost = new RouteEvaluator(LINE, KM_PARAMETERS).evaluate(DEPOT,
                route.stops().stream().map(Stop::toRouteStop).toList(), KM_TRUCK, false);
        assertThat(route.evaluation()).isEqualTo(pureCost);
        assertThat(route.evaluation().totals().costPln()).isCloseTo(75, EXACT);
        assertThat(route.objectiveValue() - route.evaluation().totals().costPln())
                .isCloseTo(objective.penaltyPln(route.stops(), route.evaluation()), EXACT)
                .isCloseTo(75, EXACT);
    }

    @Test
    void urgentStopStaysPutWhenItsWeightIsTooSmall() {
        // 20 PLN/h: 3, 2, 4 scores 60 + 20 = 80; 2, 4, 3 scores 75 + 10 = 85 -> the cheap order stays
        List<Stop> stops = List.of(stop(2, Priority.NORMAL), stop(3, Priority.NORMAL), stop(4, Priority.URGENT));

        SequencedRoute route = sequence(stops, new PriorityObjective(new PriorityWeights(20, 0)));

        assertThat(route.warehouseIds()).containsExactly(3L, 2L, 4L);
        assertThat(route.objectiveValue()).isCloseTo(80, EXACT);
    }

    @Test
    void zeroWeightsGiveTheSameRouteAsTheCostObjective() {
        List<Stop> stops = List.of(stop(2, Priority.HIGH), stop(3, Priority.NORMAL), stop(4, Priority.URGENT));

        for (StopSequencer sequencer : List.of(new StopSequencer(LINE, KM_PARAMETERS),
                new StopSequencer(LINE, KM_PARAMETERS, 0))) {
            SequencedRoute byCost = sequencer.sequence(DEPOT, stops, KM_TRUCK, false, RouteObjective.COST);
            SequencedRoute byPriority = sequencer.sequence(DEPOT, stops, KM_TRUCK, false,
                    new PriorityObjective(new PriorityWeights(0, 0)));

            assertThat(byPriority).isEqualTo(byCost);
        }
    }

    @Test
    void urgentOutweighsHigh() {
        // Equilateral triangle, 100 km sides, round trip: both orders cost 300, arrivals at 100 and 200 min.
        // HIGH 2, URGENT 3: order 2, 3 = 300 + 50 x 100/60 + 150 x 200/60 = 300 + 83.33 + 500 = 883.33
        //                   order 3, 2 = 300 + 150 x 100/60 + 50 x 200/60 = 300 + 250 + 166.67 = 716.67
        DistanceMatrix triangle = matrix(Map.of(
                pair(1, 2), 100.0, pair(2, 1), 100.0,
                pair(1, 3), 100.0, pair(3, 1), 100.0,
                pair(2, 3), 100.0, pair(3, 2), 100.0));
        List<Stop> stops = List.of(stop(2, Priority.HIGH), stop(3, Priority.URGENT));
        StopSequencer sequencer = new StopSequencer(triangle, KM_PARAMETERS);

        SequencedRoute weighted = sequencer.sequence(DEPOT, stops, KM_TRUCK, true,
                new PriorityObjective(DEFAULT_WEIGHTS));
        SequencedRoute equalWeights = sequencer.sequence(DEPOT, stops, KM_TRUCK, true,
                new PriorityObjective(new PriorityWeights(100, 100)));

        assertThat(weighted.warehouseIds()).containsExactly(3L, 2L);
        assertThat(weighted.objectiveValue()).isCloseTo(300 + 250 + 500.0 / 3, within(1e-6));
        assertThat(equalWeights.warehouseIds()).containsExactly(2L, 3L); // tie -> smaller id sequence
    }

    @Test
    void pinnedStopIsFirstEvenWhenExpensiveAndAgainstAnUrgentStop() {
        // URGENT 3, pinned NORMAL 4. Pinned: 4, 2, 3 = 75 km, arrival at 3 after 30 + 20 + 25 = 75 min
        // -> 75 + 150 x 1.25 = 262.5 (4, 3, 2 reaches 3 at 75 min too but drives 100 km).
        // Without the pin 3 goes first: 3, 2, 4 = 60 + 150 x 0.25 = 97.5.
        List<Stop> pinned = List.of(stop(2, Priority.NORMAL), stop(3, Priority.URGENT), pinnedStop(4));
        List<Stop> unpinned = List.of(stop(2, Priority.NORMAL), stop(3, Priority.URGENT), stop(4, Priority.NORMAL));
        PriorityObjective objective = new PriorityObjective(DEFAULT_WEIGHTS);

        for (StopSequencer sequencer : List.of(new StopSequencer(LINE, KM_PARAMETERS),
                new StopSequencer(LINE, KM_PARAMETERS, 0))) {
            SequencedRoute withPin = sequencer.sequence(DEPOT, pinned, KM_TRUCK, false, objective);
            SequencedRoute withoutPin = sequencer.sequence(DEPOT, unpinned, KM_TRUCK, false, objective);

            assertThat(withPin.warehouseIds()).containsExactly(4L, 2L, 3L);
            assertThat(withPin.objectiveValue()).isCloseTo(262.5, EXACT);
            assertThat(withPin.evaluation().totals().costPln()).isCloseTo(75, EXACT);
            assertThat(withoutPin.warehouseIds()).containsExactly(3L, 2L, 4L);
            assertThat(withoutPin.objectiveValue()).isCloseTo(97.5, EXACT);
        }
    }

    @Test
    void unresolvedPinConflictIsStillRejectedBySequencer() {
        List<Stop> stops = List.of(pinnedStop(2), pinnedStop(3));

        assertThat(new PriorityObjective(DEFAULT_WEIGHTS).pinnedStopFirst()).isTrue();
        assertThatThrownBy(() -> sequence(stops, new PriorityObjective(DEFAULT_WEIGHTS)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("pin conflict");
    }

    @Test
    void penaltyNeedsOneLegPerStop() {
        RouteEvaluation oneStop = new RouteEvaluator(LINE, KM_PARAMETERS).evaluate(DEPOT,
                List.of(stop(2, Priority.NORMAL).toRouteStop()), KM_TRUCK, false);
        PriorityObjective objective = new PriorityObjective(DEFAULT_WEIGHTS);

        assertThatThrownBy(() -> objective.penaltyPln(List.of(), oneStop))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PriorityObjective(null)).isInstanceOf(NullPointerException.class);
    }

    private static SequencedRoute sequence(List<Stop> stops, RouteObjective objective) {
        return new StopSequencer(LINE, KM_PARAMETERS).sequence(DEPOT, stops, KM_TRUCK, false, objective);
    }

    private static Stop stop(long warehouseId, Priority priority) {
        return new Stop(warehouseId, List.of(warehouseId * 100), 1, 1_000, priority, false, null);
    }

    private static Stop pinnedStop(long warehouseId) {
        return new Stop(warehouseId, List.of(warehouseId * 100), 1, 1_000, Priority.NORMAL, true, null);
    }

    private static DirectedPair pair(long from, long to) {
        return new DirectedPair(from, to);
    }
}
