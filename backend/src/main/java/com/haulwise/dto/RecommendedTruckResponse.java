package com.haulwise.dto;

import com.haulwise.domain.Truck;

/** A truck named in a recommendation: just enough to show the dispatcher which one it is. */
public record RecommendedTruckResponse(long id, String plate) {

    public static RecommendedTruckResponse from(Truck truck) {
        return new RecommendedTruckResponse(truck.getId(), truck.getPlate());
    }
}
