package com.haulwise.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.haulwise.solver.model.ComplianceRules;
import com.haulwise.solver.model.ComplianceWarning;
import com.haulwise.solver.model.ComplianceWarning.BreakRequired;
import com.haulwise.solver.model.ComplianceWarning.DailyDrivingExceeded;
import com.haulwise.solver.model.CostParameters;
import com.haulwise.solver.model.DirectedPair;
import com.haulwise.solver.model.DistanceProvider;
import com.haulwise.solver.model.DistanceSource;
import com.haulwise.solver.model.LegDistance;
import com.haulwise.solver.model.RouteEvaluation;
import com.haulwise.solver.model.RouteStop;
import com.haulwise.solver.model.TruckSpec;
import com.haulwise.solver.model.WarningCode;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * EU driving-time check (T-22) with the default limits: 270 min continuous, 45 min break, 540 min a day. Routes
 * from depot 1 over stops 2, 3 with 30 min service per stop; leg minutes are chosen per test.
 */
class ComplianceCheckerTest {

    private static final long DEPOT = 1;
    private static final long TRUCK = 7;
    private static final ComplianceRules DEFAULTS = new ComplianceRules(270, 45, 540);
    private static final TruckSpec TRUCK_SPEC = new TruckSpec(TRUCK, 33, 24_000, 28, 10, 45);
    private static final CostParameters PARAMETERS = new CostParameters(6.20, 30);

    private final ComplianceChecker checker = new ComplianceChecker(DEFAULTS);

    @Test
    void routeUnderTheContinuousLimitGetsNoWarning() {
        RouteEvaluation route = route(Map.of(pair(1, 2), 100.0, pair(2, 3), 100.0, pair(3, 1), 60.0), 2, 3);

        assertThat(checker.check(TRUCK, route)).isEmpty();
    }

    /** 120 + 150 = 270 min ends exactly at the limit: the day is over, no break is needed. */
    @Test
    void reachingTheLimitExactlyAtTheEndNeedsNoBreak() {
        RouteEvaluation route = route(Map.of(pair(1, 2), 120.0, pair(2, 1), 150.0), 2);

        assertThat(checker.check(TRUCK, route)).isEmpty();
    }

    /**
     * 200 min to stop 2, 30 min service (which does not reset the counter), then 100 min to stop 3: the limit is
     * reached 70 min into the second leg, i.e. 200 + 30 + 70 = 300 min after departure. After the break the
     * counter restarts: 30 + 50 min of return is fine.
     */
    @Test
    void crossingTheLimitOnceNamesTheLegAndTheMomentItIsReached() {
        RouteEvaluation route = route(Map.of(pair(1, 2), 200.0, pair(2, 3), 100.0, pair(3, 1), 50.0), 2, 3);

        List<ComplianceWarning> warnings = checker.check(TRUCK, route);

        assertThat(warnings).singleElement().isInstanceOfSatisfying(BreakRequired.class, warning -> {
            assertThat(warning.code()).isEqualTo(WarningCode.BREAK_REQUIRED);
            assertThat(warning.truckId()).isEqualTo(TRUCK);
            assertThat(warning.leg()).isEqualTo(new DirectedPair(2, 3));
            assertThat(warning.atMinutes()).isCloseTo(300, within(1e-9));
            assertThat(warning.continuousLimitMinutes()).isEqualTo(270);
            assertThat(warning.breakMinutes()).isEqualTo(45);
        });
    }

    /** One 600 min leg without return needs a break at 270 and another at 540, both on that leg; 600 > 540 a day. */
    @Test
    void aVeryLongLegNeedsABreakForEveryLimitItCrosses() {
        RouteEvaluation route = routeWithoutReturn(Map.of(pair(1, 2), 600.0), 2);

        List<ComplianceWarning> warnings = checker.check(TRUCK, route);

        assertThat(warnings).hasSize(3);
        assertThat(warnings.subList(0, 2)).allSatisfy(warning ->
                assertThat(((BreakRequired) warning).leg()).isEqualTo(new DirectedPair(1, 2)));
        assertThat(warnings.subList(0, 2)).extracting(warning -> ((BreakRequired) warning).atMinutes())
                .containsExactly(270.0, 540.0);
        assertThat(warnings.get(2)).isInstanceOf(DailyDrivingExceeded.class);
    }

    /**
     * 250 + 250 + 250 = 750 min of driving: breaks on the second leg (20 min in, at 250 + 30 + 20 = 300) and on the
     * return leg (the counter is 230 after the break, so 40 min in, at 530 + 30 + 40 = 600), and exactly one
     * DAILY_DRIVING_EXCEEDED for the whole route, after the breaks.
     */
    @Test
    void routeOverTheDailyLimitGetsExactlyOneDailyWarning() {
        RouteEvaluation route = route(Map.of(pair(1, 2), 250.0, pair(2, 3), 250.0, pair(3, 1), 250.0), 2, 3);

        List<ComplianceWarning> warnings = checker.check(TRUCK, route);

        assertThat(warnings).extracting(ComplianceWarning::code).containsExactly(WarningCode.BREAK_REQUIRED,
                WarningCode.BREAK_REQUIRED, WarningCode.DAILY_DRIVING_EXCEEDED);
        assertThat(((BreakRequired) warnings.get(0)).leg()).isEqualTo(new DirectedPair(2, 3));
        assertThat(((BreakRequired) warnings.get(0)).atMinutes()).isCloseTo(300, within(1e-9));
        assertThat(((BreakRequired) warnings.get(1)).leg()).isEqualTo(new DirectedPair(3, 1));
        assertThat(((BreakRequired) warnings.get(1)).atMinutes()).isCloseTo(600, within(1e-9));
        assertThat(warnings.get(2)).isInstanceOfSatisfying(DailyDrivingExceeded.class, daily -> {
            assertThat(daily.truckId()).isEqualTo(TRUCK);
            assertThat(daily.drivingMinutes()).isCloseTo(750, within(1e-9));
            assertThat(daily.dailyLimitMinutes()).isEqualTo(540);
        });
    }

    @Test
    void customLimitsAreUsed() {
        ComplianceChecker strict = new ComplianceChecker(new ComplianceRules(60, 15, 100));
        RouteEvaluation route = routeWithoutReturn(Map.of(pair(1, 2), 90.0), 2);

        assertThat(strict.check(TRUCK, route)).satisfiesExactly(
                warning -> assertThat(warning).isInstanceOfSatisfying(BreakRequired.class, breakRequired -> {
                    assertThat(breakRequired.atMinutes()).isCloseTo(60, within(1e-9));
                    assertThat(breakRequired.breakMinutes()).isEqualTo(15);
                }));
    }

    @Test
    void emptyRouteHasNoWarnings() {
        RouteEvaluation empty = new RouteEvaluator(provider(Map.of()), PARAMETERS)
                .evaluate(DEPOT, List.of(), TRUCK_SPEC, true);

        assertThat(checker.check(TRUCK, empty)).isEmpty();
    }

    @Test
    void rulesMustBePositive() {
        assertThatThrownBy(() -> new ComplianceRules(0, 45, 540)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxContinuousDrivingMinutes");
        assertThatThrownBy(() -> new ComplianceRules(270, 0, 540)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("breakMinutes");
        assertThatThrownBy(() -> new ComplianceRules(270, 45, 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxDailyDrivingMinutes");
    }

    private static RouteEvaluation route(Map<DirectedPair, Double> minutes, long... stops) {
        return evaluate(minutes, true, stops);
    }

    private static RouteEvaluation routeWithoutReturn(Map<DirectedPair, Double> minutes, long... stops) {
        return evaluate(minutes, false, stops);
    }

    private static RouteEvaluation evaluate(Map<DirectedPair, Double> minutes, boolean returnToDepot, long... stops) {
        List<RouteStop> routeStops = Arrays.stream(stops).mapToObj(id -> new RouteStop(id, 1_000)).toList();
        return new RouteEvaluator(provider(minutes), PARAMETERS).evaluate(DEPOT, routeStops, TRUCK_SPEC,
                returnToDepot);
    }

    /** Legs by their minutes (km = minutes; distance plays no role here). */
    private static DistanceProvider provider(Map<DirectedPair, Double> minutes) {
        Map<DirectedPair, Double> legs = new HashMap<>(minutes);
        return (from, to) -> {
            Double duration = legs.get(new DirectedPair(from, to));
            if (duration == null) {
                throw new IllegalArgumentException("No leg " + from + " -> " + to);
            }
            return new LegDistance(duration, duration, DistanceSource.SEEDED);
        };
    }

    private static DirectedPair pair(long from, long to) {
        return new DirectedPair(from, to);
    }
}
