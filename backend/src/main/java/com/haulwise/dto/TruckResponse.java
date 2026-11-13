package com.haulwise.dto;

import com.haulwise.domain.Truck;

/**
 * Map only trucks whose home warehouse is initialised (inside a transaction, or loaded with a
 * {@code findWithHomeWarehouse...} query); open-in-view is off.
 */
public record TruckResponse(
        Long id,
        String plate,
        int capacityPallets,
        int payloadKg,
        double baseConsumptionLPer100Km,
        double extraConsumptionFullLoadLPer100Km,
        double driverCostPlnPerHour,
        WarehouseSummary homeWarehouse,
        boolean available) {

    public static TruckResponse from(Truck truck) {
        return new TruckResponse(truck.getId(), truck.getPlate(), truck.getCapacityPallets(), truck.getPayloadKg(),
                truck.getBaseConsumptionLPer100Km(), truck.getExtraConsumptionFullLoadLPer100Km(),
                truck.getDriverCostPlnPerHour(), WarehouseSummary.from(truck.getHomeWarehouse()),
                truck.isAvailable());
    }
}
