package com.haulwise.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;

class HaulWisePropertiesTest {

    @Test
    void corsOriginsAreStrippedAndBlanksDropped() {
        HaulWiseProperties.Cors cors = new HaulWiseProperties.Cors(List.of(" http://localhost:5173 ", "", "http://a.test"));

        assertThat(cors.allowedOrigins()).containsExactly("http://localhost:5173", "http://a.test");
    }

    @Test
    void wildcardOriginIsRejected() {
        assertThatThrownBy(() -> new HaulWiseProperties.Cors(List.of("http://localhost:5173", "*")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("wildcards");
    }

    @Test
    void nullOriginIsRejected() {
        assertThatThrownBy(() -> new HaulWiseProperties.Cors(List.of("http://localhost:5173", "null")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("\"null\"");
    }

    @Test
    void emptyOriginListIsRejected() {
        assertThatThrownBy(() -> new HaulWiseProperties.Cors(List.of(" ")))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new HaulWiseProperties.Cors(null))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void solverGroupDefaultsToEightBruteForceStops() {
        HaulWiseProperties properties =
                new HaulWiseProperties(new HaulWiseProperties.Cors(List.of("http://localhost:5173")), null);

        assertThat(properties.solver().maxBruteForceStops()).isEqualTo(8);
        assertThat(new HaulWiseProperties.Solver(null).maxBruteForceStops()).isEqualTo(8);
    }

    @Test
    void bruteForceStopsWithinZeroToNineAreAccepted() {
        assertThat(new HaulWiseProperties.Solver(0).maxBruteForceStops()).isZero();
        assertThat(new HaulWiseProperties.Solver(9).maxBruteForceStops()).isEqualTo(9);
    }

    @Test
    void bruteForceStopsOutsideZeroToNineFailStartup() {
        assertThatThrownBy(() -> new HaulWiseProperties.Solver(10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("max-brute-force-stops must be 0..9");
        assertThatThrownBy(() -> new HaulWiseProperties.Solver(-1))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void missingCorsGroupIsRejected() {
        assertThatThrownBy(() -> new HaulWiseProperties(null, null))
                .isInstanceOf(IllegalStateException.class);
    }
}
