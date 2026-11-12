package com.haulwise.dto;

import com.haulwise.domain.Truck;

public record TruckResponse(
        Long id,
        String plate,
        int capacityPallets,
        int payloadKg,
        double baseConsumptionLPer100Km,
        double extraConsumptionFullLoadLPer100Km,
        double driverCostPlnPerHour) {

    public static TruckResponse from(Truck truck) {
        return new TruckResponse(truck.getId(), truck.getPlate(), truck.getCapacityPallets(), truck.getPayloadKg(),
                truck.getBaseConsumptionLPer100Km(), truck.getExtraConsumptionFullLoadLPer100Km(),
                truck.getDriverCostPlnPerHour());
    }
}
