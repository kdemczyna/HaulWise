package com.haulwise.exception;

/**
 * A resource addressed by id in the request path does not exist (404 {@code NOT_FOUND}).
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String resource, Object id) {
        super(resource + " " + id + " not found.");
    }
}
