package com.haulwise.solver.model;

/**
 * Plan-wide cost inputs taken from the settings (ARCHITECTURE 6.4). Truck-specific consumption, payload
 * and driver cost live in {@link TruckSpec}.
 *
 * @param dieselPricePlnPerLitre fuel price, &gt; 0
 * @param serviceMinutesPerStop unloading time at every stop, &gt;= 0
 */
public record CostParameters(double dieselPricePlnPerLitre, int serviceMinutesPerStop) {

    public CostParameters {
        if (!Double.isFinite(dieselPricePlnPerLitre) || dieselPricePlnPerLitre <= 0) {
            throw new IllegalArgumentException(
                    "dieselPricePlnPerLitre must be a finite number > 0, was " + dieselPricePlnPerLitre);
        }
        if (serviceMinutesPerStop < 0) {
            throw new IllegalArgumentException("serviceMinutesPerStop must be >= 0, was " + serviceMinutesPerStop);
        }
    }
}
