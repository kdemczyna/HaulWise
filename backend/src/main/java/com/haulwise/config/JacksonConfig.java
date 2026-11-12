package com.haulwise.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON tweaks on top of Spring Boot's auto-configured {@code ObjectMapper} (module beans are registered
 * after the standard Java time module, so they take precedence).
 */
@Configuration(proxyBeanMethods = false)
public class JacksonConfig {

    /**
     * Request date-times are local Europe/Warsaw times (D-10). The default deserializer accepts a trailing
     * {@code Z} or an offset and silently drops it; this one rejects them, so a client never gets a
     * deadline shifted without noticing. It applies to every {@code ObjectMapper} built by Spring Boot, so
     * external clients that may receive offset date-times (Claude T-24/T-25, OSRM T-26) should use their
     * own mapper or map those values to {@code OffsetDateTime}.
     */
    @Bean
    Module strictLocalDateTimeModule() {
        return new SimpleModule("StrictLocalDateTime")
                .addDeserializer(LocalDateTime.class, new StrictLocalDateTimeDeserializer());
    }

    /**
     * Accepts only ISO-8601 local date-times ({@code 2026-11-13T12:00} or {@code 2026-11-13T12:00:00});
     * an empty string is read as {@code null}, like the default deserializer.
     */
    static final class StrictLocalDateTimeDeserializer extends StdScalarDeserializer<LocalDateTime> {

        StrictLocalDateTimeDeserializer() {
            super(LocalDateTime.class);
        }

        @Override
        public LocalDateTime deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                return (LocalDateTime) context.handleUnexpectedToken(LocalDateTime.class, parser);
            }
            String text = parser.getText();
            if (text.isEmpty()) {
                return null;
            }
            try {
                return LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            } catch (DateTimeParseException ex) {
                return (LocalDateTime) context.handleWeirdStringValue(LocalDateTime.class, text,
                        "expected a local date-time without offset");
            }
        }
    }
}
