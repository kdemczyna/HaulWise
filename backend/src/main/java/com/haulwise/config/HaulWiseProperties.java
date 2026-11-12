package com.haulwise.config;

import com.haulwise.solver.StopSequencer;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of the {@code haulwise.*} keys in {@code application.yml}. Groups are added here as the
 * features that read them are implemented.
 */
@ConfigurationProperties("haulwise")
public record HaulWiseProperties(Cors cors, Solver solver) {

    public HaulWiseProperties {
        if (cors == null) {
            throw new IllegalStateException("haulwise.cors.allowed-origins must be configured.");
        }
        if (solver == null) {
            solver = new Solver(null);
        }
    }

    /**
     * Origins allowed to call {@code /api/**} cross-origin (comma-separated in the environment).
     * Wildcards and the opaque {@code null} origin are rejected: the API lists its front-ends explicitly.
     */
    public record Cors(List<String> allowedOrigins) {

        public Cors {
            allowedOrigins = allowedOrigins == null ? List.of() : allowedOrigins.stream()
                    .map(String::strip)
                    .filter(origin -> !origin.isEmpty())
                    .toList();
            if (allowedOrigins.isEmpty()) {
                throw new IllegalStateException("haulwise.cors.allowed-origins must list at least one origin.");
            }
            if (allowedOrigins.stream().anyMatch(Cors::isNotExplicit)) {
                throw new IllegalStateException(
                        "haulwise.cors.allowed-origins must list explicit origins; wildcards and \"null\" are not allowed.");
            }
        }

        private static boolean isNotExplicit(String origin) {
            return origin.contains("*") || "null".equalsIgnoreCase(origin);
        }
    }

    /**
     * Solver tuning. {@code maxBruteForceStops} is the largest stop count per truck sequenced by trying every
     * order (default {@value StopSequencer#DEFAULT_MAX_BRUTE_FORCE_STOPS}); above it nearest neighbour + 2-opt
     * is used. Values outside 0..{@value StopSequencer#MAX_BRUTE_FORCE_STOPS_LIMIT} fail startup (10! orders
     * would take too long per request).
     */
    public record Solver(Integer maxBruteForceStops) {

        public Solver {
            if (maxBruteForceStops == null) {
                maxBruteForceStops = StopSequencer.DEFAULT_MAX_BRUTE_FORCE_STOPS;
            }
            if (maxBruteForceStops < 0 || maxBruteForceStops > StopSequencer.MAX_BRUTE_FORCE_STOPS_LIMIT) {
                throw new IllegalStateException("haulwise.solver.max-brute-force-stops must be 0.."
                        + StopSequencer.MAX_BRUTE_FORCE_STOPS_LIMIT + ", was " + maxBruteForceStops + ".");
            }
        }
    }
}
