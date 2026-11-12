package com.haulwise.controller;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an endpoint that can answer 409 Conflict, so the OpenAPI document lists it. Used instead of
 * springdoc's {@code @ApiResponse}, which would replace the generated success response.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ConflictResponse {

    /** When the conflict happens, e.g. "Plate already used". */
    String value();
}
