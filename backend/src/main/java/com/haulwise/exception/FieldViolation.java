package com.haulwise.exception;

/**
 * One entry of the {@code errors[]} array of a 400 {@code VALIDATION_FAILED} problem.
 */
public record FieldViolation(String field, String message) {
}
