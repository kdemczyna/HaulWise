package com.haulwise.solver.model;

import java.util.List;
import java.util.Objects;

/**
 * The shipments the bin packer put on one truck, in loading order, with the used capacity.
 *
 * @param truck the truck
 * @param shipments shipments on the truck, in the order they were packed; copied, unmodifiable
 * @param pallets total pallets of the shipments, at most {@code truck.capacityPallets()}
 * @param weightKg total weight of the shipments, at most {@code truck.payloadKg()}
 */
public record TruckLoad(TruckSpec truck, List<ShipmentItem> shipments, int pallets, int weightKg) {

    public TruckLoad {
        Objects.requireNonNull(truck, "truck");
        shipments = List.copyOf(shipments);
        int sumPallets = shipments.stream().mapToInt(ShipmentItem::pallets).sum();
        int sumWeightKg = shipments.stream().mapToInt(ShipmentItem::weightKg).sum();
        if (pallets != sumPallets || weightKg != sumWeightKg) {
            throw new IllegalArgumentException("pallets/weightKg must equal the shipments' sums ("
                    + sumPallets + " pallets, " + sumWeightKg + " kg), were " + pallets + " / " + weightKg);
        }
        if (pallets > truck.capacityPallets() || weightKg > truck.payloadKg()) {
            throw new IllegalArgumentException("load of " + pallets + " pallets / " + weightKg
                    + " kg exceeds truck " + truck.id());
        }
    }

    /** Builds a load from its shipments, summing pallets and weight. */
    public static TruckLoad of(TruckSpec truck, List<ShipmentItem> shipments) {
        return new TruckLoad(
                truck,
                shipments,
                shipments.stream().mapToInt(ShipmentItem::pallets).sum(),
                shipments.stream().mapToInt(ShipmentItem::weightKg).sum());
    }

    /** Used pallet capacity in percent (0..100), unrounded. */
    public double palletsPct() {
        return 100.0 * pallets / truck.capacityPallets();
    }

    /** Used payload in percent (0..100), unrounded. */
    public double weightPct() {
        return 100.0 * weightKg / truck.payloadKg();
    }
}
