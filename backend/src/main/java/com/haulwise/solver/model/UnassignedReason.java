package com.haulwise.solver.model;

/**
 * Why the bin packer could not load a shipment (ARCHITECTURE 5, Plans).
 */
public enum UnassignedReason {
    /** The shipment does not fit any selected truck even when that truck is empty. */
    EXCEEDS_LARGEST_TRUCK,
    /** The shipment would fit an empty truck, but every truck that could take it is already too full. */
    NO_CAPACITY_LEFT
}
