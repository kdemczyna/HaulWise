package com.haulwise.exception;

import java.util.List;

/**
 * Cross-field or existence check failed in a service (400 {@code VALIDATION_FAILED}), reported in the
 * same {@code errors[]} shape as Bean Validation errors.
 */
public class RequestValidationException extends RuntimeException {

    private final List<FieldViolation> violations;

    public RequestValidationException(List<FieldViolation> violations) {
        super("Validation failed: " + violations);
        if (violations.isEmpty()) {
            throw new IllegalArgumentException("At least one violation is required");
        }
        this.violations = List.copyOf(violations);
    }

    public RequestValidationException(String field, String message) {
        this(List.of(new FieldViolation(field, message)));
    }

    public List<FieldViolation> getViolations() {
        return violations;
    }
}
