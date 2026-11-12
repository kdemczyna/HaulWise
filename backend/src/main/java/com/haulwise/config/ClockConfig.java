package com.haulwise.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The application's notion of "now": local time in Poland (D-10). Injected so tests can fix the date.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

    @Bean
    public Clock clock() {
        return Clock.system(ZONE);
    }
}
