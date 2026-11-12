package com.haulwise.solver.model;

/**
 * A truck as seen by the solver.
 *
 * @param id truck id
 * @param capacityPallets pallet capacity, &gt;= 1
 * @param payloadKg maximum load weight, &gt;= 1; fuel use scales with load weight / payload (D-04)
 * @param baseConsumptionLPer100Km consumption when empty, &gt; 0
 * @param extraConsumptionFullLoadLPer100Km consumption added at full payload, &gt;= 0
 * @param driverCostPlnPerHour driver cost per working hour (driving and service), &gt;= 0
 */
public record TruckSpec(
        long id,
        int capacityPallets,
        int payloadKg,
        double baseConsumptionLPer100Km,
        double extraConsumptionFullLoadLPer100Km,
        double driverCostPlnPerHour) {

    public TruckSpec {
        if (capacityPallets < 1) {
            throw new IllegalArgumentException("capacityPallets must be >= 1, was " + capacityPallets);
        }
        if (payloadKg < 1) {
            throw new IllegalArgumentException("payloadKg must be >= 1, was " + payloadKg);
        }
        if (!Double.isFinite(baseConsumptionLPer100Km) || baseConsumptionLPer100Km <= 0) {
            throw new IllegalArgumentException(
                    "baseConsumptionLPer100Km must be a finite number > 0, was " + baseConsumptionLPer100Km);
        }
        Checks.requireNonNegative(extraConsumptionFullLoadLPer100Km, "extraConsumptionFullLoadLPer100Km");
        Checks.requireNonNegative(driverCostPlnPerHour, "driverCostPlnPerHour");
    }

    /** Litres per 100 km at the given load: base + extra x loadKg / payloadKg (linear in load weight). */
    public double consumptionLPer100Km(double loadKg) {
        return baseConsumptionLPer100Km + extraConsumptionFullLoadLPer100Km * loadKg / payloadKg;
    }
}
