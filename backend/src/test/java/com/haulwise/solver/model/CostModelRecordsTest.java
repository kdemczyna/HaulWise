package com.haulwise.solver.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CostModelRecordsTest {

    private static final TruckSpec TRUCK = new TruckSpec(1, 33, 24_000, 28, 10, 45);

    @Test
    void acceptsSensibleCostParameters() {
        CostParameters parameters = new CostParameters(6.20, 0);

        assertThat(parameters.dieselPricePlnPerLitre()).isEqualTo(6.20);
        assertThat(parameters.serviceMinutesPerStop()).isZero();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsNonPositiveOrNonFiniteDieselPrice(double price) {
        assertInvalid(() -> new CostParameters(price, 30), "dieselPricePlnPerLitre");
    }

    @Test
    void rejectsNegativeServiceMinutes() {
        assertInvalid(() -> new CostParameters(6.20, -1), "serviceMinutesPerStop");
    }

    @Test
    void consumptionIsLinearInLoadWeight() {
        assertThat(TRUCK.consumptionLPer100Km(0)).isCloseTo(28, within(1e-9));
        assertThat(TRUCK.consumptionLPer100Km(12_000)).isCloseTo(33, within(1e-9));
        assertThat(TRUCK.consumptionLPer100Km(24_000)).isCloseTo(38, within(1e-9));
    }

    @Test
    void acceptsZeroExtraConsumptionAndZeroDriverCost() {
        TruckSpec truck = new TruckSpec(1, 1, 1, 0.1, 0, 0);

        assertThat(truck.consumptionLPer100Km(1)).isCloseTo(0.1, within(1e-9));
    }

    @Test
    void rejectsInvalidTruckSpecs() {
        assertInvalid(() -> new TruckSpec(1, 0, 24_000, 28, 10, 45), "capacityPallets");
        assertInvalid(() -> new TruckSpec(1, 33, 0, 28, 10, 45), "payloadKg");
        assertInvalid(() -> new TruckSpec(1, 33, 24_000, 0, 10, 45), "baseConsumptionLPer100Km");
        assertInvalid(() -> new TruckSpec(1, 33, 24_000, Double.NaN, 10, 45), "baseConsumptionLPer100Km");
        assertInvalid(() -> new TruckSpec(1, 33, 24_000, 28, -0.5, 45), "extraConsumptionFullLoadLPer100Km");
        assertInvalid(() -> new TruckSpec(1, 33, 24_000, 28, 10, -1), "driverCostPlnPerHour");
        assertInvalid(() -> new TruckSpec(1, 33, 24_000, 28, 10, Double.POSITIVE_INFINITY), "driverCostPlnPerHour");
    }

    @Test
    void rejectsNegativeDropWeight() {
        assertInvalid(() -> new RouteStop(2, -1), "dropWeightKg");
    }

    @Test
    void rejectsInvalidLegs() {
        LegDistance distance = new LegDistance(100, 60, DistanceSource.SEEDED);

        assertThatThrownBy(() -> new Leg(1, 2, null, 0, 60, 30, 28, 168, 67.5))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("distance");
        assertInvalid(() -> new Leg(1, 2, distance, -1, 60, 30, 28, 168, 67.5), "loadWeightKg");
        assertInvalid(() -> new Leg(1, 2, distance, 0, Double.NaN, 30, 28, 168, 67.5), "arrivalMinutes");
        assertInvalid(() -> new Leg(1, 2, distance, 0, 60, -30, 28, 168, 67.5), "serviceMinutes");
        assertInvalid(() -> new Leg(1, 2, distance, 0, 60, 30, -28, 168, 67.5), "litres");
        assertInvalid(() -> new Leg(1, 2, distance, 0, 60, 30, 28, -168, 67.5), "fuelCostPln");
        assertInvalid(() -> new Leg(1, 2, distance, 0, 60, 30, 28, 168, -67.5), "driverCostPln");
    }

    @Test
    void totalsAddUpFieldByField() {
        Totals sum = new Totals(100, 60, 30, 33, 198, 67.5).plus(new Totals(50, 45, 30, 15.25, 91.5, 56.25));

        assertThat(sum).isEqualTo(new Totals(150, 105, 60, 48.25, 289.5, 123.75));
        assertThat(sum.totalMinutes()).isEqualTo(165);
        assertThat(sum.costPln()).isEqualTo(413.25);
        assertThat(Totals.ZERO.plus(sum)).isEqualTo(sum);
    }

    private static void assertInvalid(ThrowingCallable creation, String field) {
        assertThatThrownBy(creation).isInstanceOf(IllegalArgumentException.class).hasMessageContaining(field);
    }
}
