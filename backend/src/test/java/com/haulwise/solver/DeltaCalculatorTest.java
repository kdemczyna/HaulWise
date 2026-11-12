package com.haulwise.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.haulwise.solver.model.Delta;
import com.haulwise.solver.model.Mode;
import com.haulwise.solver.model.PlanVariant;
import com.haulwise.solver.model.Priority;
import com.haulwise.solver.model.RouteEvaluation;
import com.haulwise.solver.model.RoutePlan;
import com.haulwise.solver.model.SequencedRoute;
import com.haulwise.solver.model.SequencingMethod;
import com.haulwise.solver.model.ShipmentItem;
import com.haulwise.solver.model.Stop;
import com.haulwise.solver.model.Totals;
import com.haulwise.solver.model.TruckLoad;
import com.haulwise.solver.model.TruckSpec;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Delta numbers and the sentence rules of ARCHITECTURE 6.5. Variants are built by hand: a route is a list of
 * stops with given totals (the calculator reads only the stop order and the totals).
 */
class DeltaCalculatorTest {

    private static final Map<Long, String> CITIES = Map.of(
            2L, "Lodz", 3L, "Krakow", 4L, "Lublin", 5L, "Gdansk", 6L, "Poznan", 7L, "Szczecin");

    private final DeltaCalculator calculator = new DeltaCalculator();

    @Test
    void noLoadedTruckMeansNothingToCompare() {
        Delta delta = calculator.calculate(variant(Mode.OPTIMAL), variant(Mode.PRIORITY), CITIES);

        assertThat(delta.costPln()).isZero();
        assertThat(delta.distanceKm()).isZero();
        assertThat(delta.noExtraCost()).isTrue();
        assertThat(delta.sentence())
                .isEqualTo("No shipments could be loaded onto the selected trucks, so there is nothing to compare.");
    }

    @Test
    void deltaIsPriorityMinusOptimalForEveryValue() {
        Totals optimal = new Totals(100, 90, 60, 30, 186, 112.5);
        Totals priority = new Totals(138, 131, 60, 42.4, 262.88, 143.25);

        Delta delta = calculator.calculate(
                variant(Mode.OPTIMAL, route(1, optimal, normal(2))),
                variant(Mode.PRIORITY, route(1, priority, normal(2))), CITIES);

        assertThat(delta.distanceKm()).isCloseTo(38, within(1e-9));
        assertThat(delta.durationMinutes()).isCloseTo(41, within(1e-9));
        assertThat(delta.litres()).isCloseTo(12.4, within(1e-9));
        assertThat(delta.fuelCostPln()).isCloseTo(76.88, within(1e-9));
        assertThat(delta.driverCostPln()).isCloseTo(30.75, within(1e-9));
        assertThat(delta.costPln()).isCloseTo(107.63, within(1e-9));
        assertThat(delta.noExtraCost()).isFalse();
    }

    @Test
    void sumsOverAllTrucks() {
        Delta delta = calculator.calculate(
                variant(Mode.OPTIMAL, route(1, km(100), normal(2)), route(2, km(50), normal(3))),
                variant(Mode.PRIORITY, route(1, km(110), normal(2)), route(2, km(65), normal(3))), CITIES);

        assertThat(delta.distanceKm()).isCloseTo(25, within(1e-9));
        assertThat(delta.costPln()).isCloseTo(25, within(1e-9));
    }

    @Test
    void identicalVariantsCostNothingExtra() {
        Delta delta = calculator.calculate(
                variant(Mode.OPTIMAL, route(1, km(100), urgent(4), normal(2))),
                variant(Mode.PRIORITY, route(1, km(100), urgent(4), normal(2))), CITIES);

        assertThat(delta.noExtraCost()).isTrue();
        assertThat(delta.sentence()).isEqualTo("Respecting priorities costs nothing extra.");
        assertThat(delta.costPln()).isZero();
        assertThat(delta.distanceKm()).isZero();
    }

    @ParameterizedTest(name = "cost {0} PLN, distance {1} km -> noExtraCost {2}")
    @CsvSource({
            "0.49, 0.49, true",
            "0.50, 0, false",
            "0, 0.5, false",
            "0.49, -0.49, true",
            "0, -0.5, false"})
    void noExtraCostNeedsBothCostAndDistanceBelowTheThresholds(double costPln, double distanceKm, boolean expected) {
        Totals optimal = new Totals(100, 100, 0, 10, 50, 50);
        Totals priority = new Totals(100 + distanceKm, 100, 0, 10, 50 + costPln, 50);

        Delta delta = calculator.calculate(
                variant(Mode.OPTIMAL, route(1, optimal, normal(2))),
                variant(Mode.PRIORITY, route(1, priority, normal(2))), CITIES);

        assertThat(delta.noExtraCost()).isEqualTo(expected);
    }

    @Test
    void singleMovedPinnedStopIsDeliveredFirst() {
        Delta delta = calculator.calculate(
                variant(Mode.OPTIMAL, route(1, minutes(100, 100), normal(2), pinned(5))),
                variant(Mode.PRIORITY, route(1, minutes(138, 141), pinned(5), normal(2))), CITIES);

        assertThat(delta.sentence()).isEqualTo("Delivering Gdansk first costs 38.00 PLN more (38 km and 41 min longer).");
    }

    @Test
    void movedUrgentAndHighStopsArePrioritised() {
        Delta delta = calculator.calculate(
                variant(Mode.OPTIMAL, route(1, km(100), normal(2), high(3), urgent(4))),
                variant(Mode.PRIORITY, route(1, km(130), urgent(4), high(3), normal(2))), CITIES);

        // Krakow keeps position 2, so only Lublin moved earlier.
        assertThat(delta.sentence()).isEqualTo("Prioritising Lublin costs 30.00 PLN more (30 km and 30 min longer).");
    }

    @Test
    void stopsPushedLaterByAPinAreNotNamed() {
        // The seed story: the pin puts Gdansk first, which pushes the URGENT Lublin from position 1 to 2.
        Delta delta = calculator.calculate(
                variant(Mode.OPTIMAL, route(1, km(100), urgent(4), high(3), pinned(5))),
                variant(Mode.PRIORITY, route(1, km(185), pinned(5), urgent(4), high(3))), CITIES);

        assertThat(delta.sentence()).isEqualTo("Delivering Gdansk first costs 85.00 PLN more (85 km and 1 h 25 min longer).");
    }

    @Test
    void namesPinnedThenUrgentThenHighAcrossTrucksEachCityOnce() {
        Delta delta = calculator.calculate(
                variant(Mode.OPTIMAL,
                        route(1, km(100), normal(2), high(3), urgent(4)),
                        route(2, km(100), normal(6), pinned(5), urgent(4))),
                variant(Mode.PRIORITY,
                        route(1, km(110), urgent(4), high(3), normal(2)),
                        route(2, km(110), pinned(5), urgent(4), normal(6))), CITIES);

        assertThat(delta.sentence())
                .isEqualTo("Prioritising Gdansk and Lublin costs 20.00 PLN more (20 km and 20 min longer).");
    }

    @Test
    void namesAtMostThreeCitiesThenCountsTheRest() {
        Delta delta = calculator.calculate(
                variant(Mode.OPTIMAL,
                        route(1, km(100), normal(2), high(3), urgent(4)),
                        route(2, km(100), normal(6), pinned(5), high(7))),
                variant(Mode.PRIORITY,
                        route(1, km(100), high(3), urgent(4), normal(2)),
                        route(2, km(101), pinned(5), high(7), normal(6))), CITIES);

        // All four moved earlier: Gdansk (pinned), Lublin (URGENT), Krakow (HIGH, truck 1), Szczecin (HIGH, truck 2).
        assertThat(delta.sentence())
                .isEqualTo("Prioritising Gdansk, Lublin, Krakow and 1 more costs 1.00 PLN more (1 km and 1 min longer).");
    }

    @Test
    void fallsBackToRespectingPrioritiesWhenNoImportantStopMovedEarlier() {
        Delta delta = calculator.calculate(
                variant(Mode.OPTIMAL, route(1, km(100), normal(2), normal(3))),
                variant(Mode.PRIORITY, route(1, km(110), normal(3), normal(2))), CITIES);

        assertThat(delta.sentence()).isEqualTo("Respecting priorities costs 10.00 PLN more (10 km and 10 min longer).");
    }

    @Test
    void rejectsSwappedModes() {
        PlanVariant optimal = variant(Mode.OPTIMAL, route(1, km(1), normal(2)));
        PlanVariant priority = variant(Mode.PRIORITY, route(1, km(1), normal(2)));

        assertThatThrownBy(() -> calculator.calculate(priority, optimal, CITIES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("OPTIMAL and a PRIORITY");
    }

    @Test
    void rejectsVariantsOverDifferentTrucks() {
        assertThatThrownBy(() -> calculator.calculate(
                variant(Mode.OPTIMAL, route(1, km(1), normal(2))),
                variant(Mode.PRIORITY, route(2, km(1), normal(2))), CITIES))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same trucks");
    }

    @Test
    void rejectsAMovedStopWithoutCityName() {
        assertThatThrownBy(() -> calculator.calculate(
                variant(Mode.OPTIMAL, route(1, km(100), normal(2), urgent(4))),
                variant(Mode.PRIORITY, route(1, km(110), urgent(4), normal(2))), Map.of(2L, "Lodz")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("warehouse 4");
    }

    @ParameterizedTest(name = "{1} km, {2} PLN, {3} min -> {4}")
    @CsvSource(delimiter = '|', value = {
            "Delivering Gdansk first | 85 | 292.97375 | 75 | Delivering Gdansk first costs 292.97 PLN more (85 km and 1 h 15 min longer).",
            "Prioritising Lublin | -12 | 30 | 40 | Prioritising Lublin costs 30.00 PLN more (12 km shorter, 40 min longer).",
            "Prioritising Lublin | 12 | 30 | -40 | Prioritising Lublin costs 30.00 PLN more (12 km longer, 40 min shorter).",
            "Prioritising Lublin | -12 | 30 | -40 | Prioritising Lublin costs 30.00 PLN more (12 km and 40 min shorter).",
            "Prioritising Lublin | 0.4 | 30 | 40 | Prioritising Lublin costs 30.00 PLN more (40 min longer).",
            "Prioritising Lublin | 7 | 3 | -0.4 | Prioritising Lublin costs 3.00 PLN more (7 km longer).",
            "Prioritising Lublin | 0.4 | 0.6 | 0.4 | Prioritising Lublin costs 0.60 PLN more.",
            "Prioritising Lublin | 38 | 112.345 | 41 | Prioritising Lublin costs 112.35 PLN more (38 km and 41 min longer).",
            "Prioritising Lublin | 37.5 | 112.344 | 120 | Prioritising Lublin costs 112.34 PLN more (38 km and 2 h longer).",
            "Prioritising Lublin | 1 | 1234.5 | 59.5 | Prioritising Lublin costs 1234.50 PLN more (1 km and 1 h longer).",
            "Respecting priorities | -1 | -3.1 | 0 | Respecting priorities costs 3.10 PLN less (1 km shorter)."})
    void wholeSentences(String subject, double km, double costPln, double minutes, String expected) {
        assertThat(DeltaCalculator.sentence(subject, km, costPln, minutes)).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0} min -> {1}")
    @CsvSource({"1, 1 min", "41, 41 min", "59, 59 min", "60, 1 h", "75, 1 h 15 min", "120, 2 h", "125, 2 h 5 min"})
    void formatsDurations(int minutes, String expected) {
        assertThat(DeltaCalculator.duration(minutes)).isEqualTo(expected);
    }

    @Test
    void negativeDistanceReadsShorterInTheDelta() {
        // PRIORITY is 12 km shorter but 40 min slower (and so 30 PLN dearer in driver time).
        Totals optimal = new Totals(100, 100, 0, 10, 100, 0);
        Totals priority = new Totals(88, 140, 0, 8.8, 88, 42);

        Delta delta = calculator.calculate(
                variant(Mode.OPTIMAL, route(1, optimal, normal(2), urgent(4))),
                variant(Mode.PRIORITY, route(1, priority, urgent(4), normal(2))), CITIES);

        assertThat(delta.distanceKm()).isCloseTo(-12, within(1e-9));
        assertThat(delta.costPln()).isCloseTo(30, within(1e-9));
        assertThat(delta.sentence()).isEqualTo("Prioritising Lublin costs 30.00 PLN more (12 km shorter, 40 min longer).");
    }

    @Test
    void samePinnedCityOnTwoTrucksIsStillDeliveredFirst() {
        Delta delta = calculator.calculate(
                variant(Mode.OPTIMAL,
                        route(1, km(100), normal(2), pinned(5)),
                        route(2, km(100), normal(6), pinned(5))),
                variant(Mode.PRIORITY,
                        route(1, km(110), pinned(5), normal(2)),
                        route(2, km(110), pinned(5), normal(6))), CITIES);

        assertThat(delta.sentence()).isEqualTo("Delivering Gdansk first costs 20.00 PLN more (20 km and 20 min longer).");
    }

    @Test
    void pinnedStopAlreadyFirstInOptimalIsNotNamed() {
        Delta delta = calculator.calculate(
                variant(Mode.OPTIMAL, route(1, km(100), pinned(5), normal(2), urgent(4))),
                variant(Mode.PRIORITY, route(1, km(110), pinned(5), urgent(4), normal(2))), CITIES);

        assertThat(delta.sentence()).isEqualTo("Prioritising Lublin costs 10.00 PLN more (10 km and 10 min longer).");
    }

    @Test
    void joinsCitiesInPlainEnglish() {
        assertThat(DeltaCalculator.joinCities(List.of("Gdansk"))).isEqualTo("Gdansk");
        assertThat(DeltaCalculator.joinCities(List.of("Gdansk", "Lublin"))).isEqualTo("Gdansk and Lublin");
        assertThat(DeltaCalculator.joinCities(List.of("Gdansk", "Lublin", "Krakow")))
                .isEqualTo("Gdansk, Lublin and Krakow");
        assertThat(DeltaCalculator.joinCities(List.of("Gdansk", "Lublin", "Krakow", "Lodz")))
                .isEqualTo("Gdansk, Lublin, Krakow and 1 more");
        assertThat(DeltaCalculator.joinCities(List.of("Gdansk", "Lublin", "Krakow", "Lodz", "Poznan")))
                .isEqualTo("Gdansk, Lublin, Krakow and 2 more");
        assertThatThrownBy(() -> DeltaCalculator.joinCities(List.of())).isInstanceOf(IllegalArgumentException.class);
    }

    // --- fixtures -------------------------------------------------------------------------------------------

    /** Cost in PLN = km, working minutes = km. */
    private static Totals km(double km) {
        return minutes(km, km);
    }

    private static Totals minutes(double km, double minutes) {
        return new Totals(km, minutes, 0, km / 10, km, 0);
    }

    private static PlanVariant variant(Mode mode, RoutePlan... routes) {
        return new PlanVariant(mode, List.of(routes));
    }

    private static RoutePlan route(long truckId, Totals totals, Stop... stops) {
        TruckSpec truck = new TruckSpec(truckId, 33, 24_000, 10, 0, 0);
        List<ShipmentItem> shipments = Arrays.stream(stops)
                .map(stop -> new ShipmentItem(stop.shipmentIds().get(0), stop.warehouseId(), stop.dropPallets(),
                        stop.dropWeightKg(), stop.maxPriority(), stop.pinned(), null))
                .toList();
        RouteEvaluation evaluation = new RouteEvaluation(List.of(), Optional.empty(), totals);
        SequencedRoute route = new SequencedRoute(List.of(stops), evaluation, totals.costPln(),
                SequencingMethod.BRUTE_FORCE);
        return new RoutePlan(TruckLoad.of(truck, shipments), route);
    }

    private static Stop normal(long warehouseId) {
        return stop(warehouseId, Priority.NORMAL, false);
    }

    private static Stop high(long warehouseId) {
        return stop(warehouseId, Priority.HIGH, false);
    }

    private static Stop urgent(long warehouseId) {
        return stop(warehouseId, Priority.URGENT, false);
    }

    private static Stop pinned(long warehouseId) {
        return stop(warehouseId, Priority.NORMAL, true);
    }

    private static Stop stop(long warehouseId, Priority priority, boolean pinned) {
        return new Stop(warehouseId, List.of(100 + warehouseId), 1, 100, priority, pinned, null);
    }
}
