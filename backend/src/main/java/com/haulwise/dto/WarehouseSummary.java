package com.haulwise.dto;

import com.haulwise.domain.Warehouse;

/**
 * Origin/destination embedded in a {@link ShipmentResponse}, so the UI needs no extra lookup.
 */
public record WarehouseSummary(Long id, String name, String city) {

    public static WarehouseSummary from(Warehouse warehouse) {
        return new WarehouseSummary(warehouse.getId(), warehouse.getName(), warehouse.getCity());
    }
}
