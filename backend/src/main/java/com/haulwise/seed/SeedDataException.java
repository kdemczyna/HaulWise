package com.haulwise.seed;

/**
 * A bundled seed file is malformed. The message names the file and line so the data can be fixed.
 */
public class SeedDataException extends RuntimeException {

    public SeedDataException(String message) {
        super(message);
    }
}
