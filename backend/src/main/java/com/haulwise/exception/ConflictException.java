package com.haulwise.exception;

/**
 * The request clashes with the current state, e.g. a duplicate name or a referenced delete
 * (409 {@code CONFLICT}). The message is shown to the user as the problem {@code detail}.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
