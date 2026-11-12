package com.haulwise.dto.plan;

import com.haulwise.solver.model.UnassignedReason;

/** A selected shipment no truck could take, with the reason and the numbers involved. */
public record UnassignedResponse(long shipmentId, String reference, UnassignedReason reason, String message) {
}
