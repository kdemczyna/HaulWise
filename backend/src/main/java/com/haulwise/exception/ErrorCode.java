package com.haulwise.exception;

/**
 * Machine-readable {@code code} property of every problem response (section 5.1 of ARCHITECTURE).
 */
public enum ErrorCode {
    VALIDATION_FAILED,
    MALFORMED_REQUEST,
    NOT_FOUND,
    CONFLICT,
    INTERNAL_ERROR
}
