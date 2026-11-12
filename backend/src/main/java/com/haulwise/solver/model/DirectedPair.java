package com.haulwise.solver.model;

/**
 * Key of a directed leg: from one warehouse to another.
 */
public record DirectedPair(long fromId, long toId) {
}
