package com.haulwise;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Shared Spring Boot test setup: isolated in-memory database per context, no seed data,
 * no H2 console and AI disabled regardless of a developer's local {@code .env}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:test-${random.uuid};DB_CLOSE_DELAY=-1",
        "spring.h2.console.enabled=false",
        "haulwise.seed.enabled=false",
        "haulwise.ai.api-key="
})
public @interface HaulWiseApiTest {
}
