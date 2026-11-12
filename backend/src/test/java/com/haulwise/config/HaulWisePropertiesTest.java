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
    void missingCorsGroupIsRejected() {
        assertThatThrownBy(() -> new HaulWiseProperties(null))
                .isInstanceOf(IllegalStateException.class);
    }
}
