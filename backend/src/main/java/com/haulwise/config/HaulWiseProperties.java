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
            solver = new Solver(null, null);
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
     * <p>
     * {@code maxRecommendationTrucks} is the largest fleet size the truck recommendation (T-30) enumerates
     * exhaustively (default {@value #DEFAULT_MAX_RECOMMENDATION_TRUCKS}, i.e. up to 127 subsets); above it, a
     * greedy fallback is used instead (documented limitation, not a bug). A fleet at the startup-validated
     * limit of {@value #MAX_RECOMMENDATION_TRUCKS_LIMIT} measured ~21s per request with max-brute-force-stops-
     * sized routes, so the default is kept well below that limit; raise it only for a smaller/faster fleet.
     * Values outside 0..{@value #MAX_RECOMMENDATION_TRUCKS_LIMIT} fail startup (13 trucks would be 8191
     * subsets per request).
     */
    public record Solver(Integer maxBruteForceStops, Integer maxRecommendationTrucks) {

        /** Default cap on exhaustive fleet-subset search (2^7 - 1 = 127 subsets). */
        public static final int DEFAULT_MAX_RECOMMENDATION_TRUCKS = 7;
        /** Largest cap accepted at startup (2^12 - 1 = 4095 subsets). */
        public static final int MAX_RECOMMENDATION_TRUCKS_LIMIT = 12;

        public Solver {
            if (maxBruteForceStops == null) {
                maxBruteForceStops = StopSequencer.DEFAULT_MAX_BRUTE_FORCE_STOPS;
            }
            if (maxBruteForceStops < 0 || maxBruteForceStops > StopSequencer.MAX_BRUTE_FORCE_STOPS_LIMIT) {
                throw new IllegalStateException("haulwise.solver.max-brute-force-stops must be 0.."
                        + StopSequencer.MAX_BRUTE_FORCE_STOPS_LIMIT + ", was " + maxBruteForceStops + ".");
            }
            if (maxRecommendationTrucks == null) {
                maxRecommendationTrucks = DEFAULT_MAX_RECOMMENDATION_TRUCKS;
            }
            if (maxRecommendationTrucks < 0 || maxRecommendationTrucks > MAX_RECOMMENDATION_TRUCKS_LIMIT) {
                throw new IllegalStateException("haulwise.solver.max-recommendation-trucks must be 0.."
                        + MAX_RECOMMENDATION_TRUCKS_LIMIT + ", was " + maxRecommendationTrucks + ".");
            }
        }
    }
}
