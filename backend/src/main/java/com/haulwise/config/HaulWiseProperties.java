package com.haulwise.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed view of the {@code haulwise.*} keys in {@code application.yml}. Groups are added here as the
 * features that read them are implemented.
 */
@ConfigurationProperties("haulwise")
public record HaulWiseProperties(Cors cors) {

    public HaulWiseProperties {
        if (cors == null) {
            throw new IllegalStateException("haulwise.cors.allowed-origins must be configured.");
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
}
