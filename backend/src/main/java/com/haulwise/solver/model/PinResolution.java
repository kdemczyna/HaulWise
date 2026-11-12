package com.haulwise.solver.model;

import java.util.List;

/**
 * One truck's stops with at most one pinned stop, and the PIN_CONFLICT warning when pins had to be dropped.
 *
 * @param stops the truck's stops, at most one of them pinned; copied, unmodifiable
 * @param warnings empty, or the single PIN_CONFLICT warning; copied, unmodifiable
 */
public record PinResolution(List<Stop> stops, List<Warning> warnings) {

    public PinResolution {
        stops = List.copyOf(stops);
        warnings = List.copyOf(warnings);
        if (stops.stream().filter(Stop::pinned).count() > 1) {
            throw new IllegalArgumentException("At most one stop may stay pinned");
        }
    }
}
