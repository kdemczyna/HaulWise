package com.haulwise.dto;

import com.haulwise.domain.Warehouse;

public record WarehouseResponse(Long id, String name, String city, double lat, double lon) {

    public static WarehouseResponse from(Warehouse warehouse) {
        return new WarehouseResponse(warehouse.getId(), warehouse.getName(), warehouse.getCity(),
                warehouse.getLat(), warehouse.getLon());
    }
}
