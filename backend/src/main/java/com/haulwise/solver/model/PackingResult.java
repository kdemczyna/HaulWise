package com.haulwise.solver.model;

import java.util.List;

/**
 * Output of the bin packer: shared by both plan variants (D-03).
 *
 * @param loads one entry per truck that received at least one shipment, in the packer's truck order;
 *              copied, unmodifiable
 * @param unassigned shipments that could not be loaded, in the packer's shipment order; copied,
 *                   unmodifiable
 */
public record PackingResult(List<TruckLoad> loads, List<Unassigned> unassigned) {

    public PackingResult {
        loads = List.copyOf(loads);
        unassigned = List.copyOf(unassigned);
    }
}
