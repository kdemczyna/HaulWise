package com.haulwise.solver.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PriorityRecordsTest {

    @Test
    void weightsMapEveryPriorityAndNormalWeighsNothing() {
        PriorityWeights weights = new PriorityWeights(150, 50);

        assertThat(weights.plnPerHour(Priority.URGENT)).isEqualTo(150);
        assertThat(weights.plnPerHour(Priority.HIGH)).isEqualTo(50);
        assertThat(weights.plnPerHour(Priority.NORMAL)).isZero();
    }

    @Test
    void weightsMayBeZeroButNotNegativeOrNonFinite() {
        assertThat(new PriorityWeights(0, 0).plnPerHour(Priority.URGENT)).isZero();
        assertThatThrownBy(() -> new PriorityWeights(-1, 50)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("urgentPlnPerHour");
        assertThatThrownBy(() -> new PriorityWeights(150, -0.01)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("highPlnPerHour");
        assertThatThrownBy(() -> new PriorityWeights(Double.NaN, 50)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PriorityWeights(150, Double.POSITIVE_INFINITY))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void warningCopiesItsShipmentIdsAndNeedsAMessage() {
        List<Long> ids = new ArrayList<>(List.of(7L));
        Warning warning = new Warning(WarningCode.PIN_CONFLICT, "Pin dropped", 1, ids);
        ids.add(8L);

        assertThat(warning.shipmentIds()).containsExactly(7L);
        assertThatThrownBy(() -> new Warning(WarningCode.PIN_CONFLICT, " ", 1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Warning(null, "x", 1, List.of())).isInstanceOf(NullPointerException.class);
    }

    @Test
    void unpinnedKeepsEverythingButThePin() {
        Stop pinned = new Stop(3, List.of(30L), 2, 500, Priority.HIGH, true, null);

        assertThat(pinned.unpinned()).isEqualTo(new Stop(3, List.of(30L), 2, 500, Priority.HIGH, false, null));
    }

    @Test
    void pinResolutionAllowsAtMostOnePinnedStop() {
        Stop a = new Stop(2, List.of(20L), 1, 100, Priority.NORMAL, true, null);
        Stop b = new Stop(3, List.of(30L), 1, 100, Priority.NORMAL, true, null);

        assertThat(new PinResolution(List.of(a, b.unpinned()), List.of()).stops()).hasSize(2);
        assertThatThrownBy(() -> new PinResolution(List.of(a, b), List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
