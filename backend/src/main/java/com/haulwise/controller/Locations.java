package com.haulwise.controller;

import java.net.URI;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Builds the {@code Location} header of a 201 response: the current collection URL plus the new id.
 */
final class Locations {

    private Locations() {
    }

    static URI of(Object id) {
        return ServletUriComponentsBuilder.fromCurrentRequestUri().path("/{id}").buildAndExpand(id).toUri();
    }
}
