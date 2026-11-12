package com.haulwise.solver;

import com.haulwise.solver.model.PackingResult;
import com.haulwise.solver.model.ShipmentItem;
import com.haulwise.solver.model.TruckLoad;
import com.haulwise.solver.model.TruckSpec;
import com.haulwise.solver.model.Unassigned;
import com.haulwise.solver.model.UnassignedReason;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.ToLongFunction;

/**
 * First-fit-decreasing assignment of shipments to trucks (ARCHITECTURE 6.1 step 1). Runs once per plan;
 * both variants share the result (D-03).
 * <ul>
 *   <li>Shipments are taken in {@link #SHIPMENT_ORDER}: priority (URGENT, HIGH, NORMAL), pinned first,
 *       pallets descending, weight descending, id ascending.</li>
 *   <li>Trucks are tried in {@link #TRUCK_ORDER}: capacity in pallets descending, payload descending, id
 *       ascending.</li>
 *   <li>Each shipment goes, whole, onto the first truck whose remaining pallets <em>and</em> remaining kg
 *       both suffice.</li>
 *   <li>A shipment that fits no truck is unassigned: {@code EXCEEDS_LARGEST_TRUCK} when it would not fit
 *       any of the trucks even empty (or there are no trucks), otherwise {@code NO_CAPACITY_LEFT}.</li>
 *   <li>The {@code EXCEEDS_LARGEST_TRUCK} message names the pallet limit when the shipment has more
 *       pallets than any truck (also when its weight is too high as well: pallets take precedence), else
 *       the payload limit when it is heavier than any truck, else says it fits no single truck on both
 *       limits.</li>
 * </ul>
 * Geography is ignored (D-03). The result does not depend on the order of the input lists.
 * Stateless and thread-safe.
 */
public final class BinPacker {

    /** Most important first, then the biggest, then by id so equal shipments have a stable order. */
    public static final Comparator<ShipmentItem> SHIPMENT_ORDER = Comparator
            .comparing(ShipmentItem::priority)
            .thenComparing(ShipmentItem::pinnedFirst, Comparator.reverseOrder())
            .thenComparing(ShipmentItem::pallets, Comparator.reverseOrder())
            .thenComparing(ShipmentItem::weightKg, Comparator.reverseOrder())
            .thenComparingLong(ShipmentItem::id);

    /** Biggest truck first, then by id so equal trucks have a stable order. */
    public static final Comparator<TruckSpec> TRUCK_ORDER = Comparator
            .comparing(TruckSpec::capacityPallets, Comparator.reverseOrder())
            .thenComparing(TruckSpec::payloadKg, Comparator.reverseOrder())
            .thenComparingLong(TruckSpec::id);

    /**
     * @param shipments shipments to load; ids must be distinct
     * @param trucks available trucks; ids must be distinct
     * @return loads of the trucks that received shipments, in {@link #TRUCK_ORDER}, and the unassigned
     *         shipments in {@link #SHIPMENT_ORDER}
     * @throws IllegalArgumentException for duplicate shipment or truck ids
     */
    public PackingResult pack(List<ShipmentItem> shipments, List<TruckSpec> trucks) {
        requireDistinctIds(shipments, ShipmentItem::id, "shipment");
        requireDistinctIds(trucks, TruckSpec::id, "truck");

        List<Bin> bins = trucks.stream().sorted(TRUCK_ORDER).map(Bin::new).toList();
        List<Unassigned> unassigned = new ArrayList<>();
        for (ShipmentItem shipment : shipments.stream().sorted(SHIPMENT_ORDER).toList()) {
            bins.stream()
                    .filter(bin -> bin.fits(shipment))
                    .findFirst()
                    .ifPresentOrElse(bin -> bin.add(shipment), () -> unassigned.add(unassigned(shipment, trucks)));
        }

        List<TruckLoad> loads = bins.stream()
                .filter(bin -> !bin.shipments.isEmpty())
                .map(bin -> TruckLoad.of(bin.truck, bin.shipments))
                .toList();
        return new PackingResult(loads, unassigned);
    }

    private static Unassigned unassigned(ShipmentItem shipment, List<TruckSpec> trucks) {
        boolean fitsAnEmptyTruck = trucks.stream().anyMatch(truck -> fitsEmpty(shipment, truck));
        if (fitsAnEmptyTruck) {
            return new Unassigned(shipment.id(), UnassignedReason.NO_CAPACITY_LEFT,
                    "No capacity left for " + pallets(shipment.pallets()) + " / " + shipment.weightKg()
                            + " kg: the selected trucks are already loaded.");
        }
        return new Unassigned(shipment.id(), UnassignedReason.EXCEEDS_LARGEST_TRUCK,
                exceedsMessage(shipment, trucks));
    }

    private static String exceedsMessage(ShipmentItem shipment, List<TruckSpec> trucks) {
        if (trucks.isEmpty()) {
            return "No trucks are selected.";
        }
        int maxPallets = trucks.stream().mapToInt(TruckSpec::capacityPallets).max().orElseThrow();
        int maxPayloadKg = trucks.stream().mapToInt(TruckSpec::payloadKg).max().orElseThrow();
        // Checked first, so pallets are reported when both limits are exceeded. The shipment then has more
        // pallets than a truck with >= 1, i.e. at least 2, so the plural verb is always right.
        if (shipment.pallets() > maxPallets) {
            return pallets(shipment.pallets()) + " exceed the largest selected truck (" + pallets(maxPallets)
                    + ").";
        }
        if (shipment.weightKg() > maxPayloadKg) {
            return shipment.weightKg() + " kg exceed the largest payload of the selected trucks ("
                    + maxPayloadKg + " kg).";
        }
        return pallets(shipment.pallets()) + " / " + shipment.weightKg()
                + " kg do not fit any single selected truck.";
    }

    /** "1 pallet", "2 pallets" ("kg" is a unit symbol and needs no plural). */
    static String pallets(int count) {
        return count + (count == 1 ? " pallet" : " pallets");
    }

    private static boolean fitsEmpty(ShipmentItem shipment, TruckSpec truck) {
        return shipment.pallets() <= truck.capacityPallets() && shipment.weightKg() <= truck.payloadKg();
    }

    private static <T> void requireDistinctIds(List<T> items, ToLongFunction<T> id, String kind) {
        Objects.requireNonNull(items, kind + "s");
        Set<Long> seen = new HashSet<>();
        for (T item : items) {
            Objects.requireNonNull(item, kind);
            if (!seen.add(id.applyAsLong(item))) {
                throw new IllegalArgumentException("duplicate " + kind + " id " + id.applyAsLong(item));
            }
        }
    }

    /** Mutable working state for one truck during packing; never leaves this class. */
    private static final class Bin {
        private final TruckSpec truck;
        private final List<ShipmentItem> shipments = new ArrayList<>();
        private int pallets;
        private int weightKg;

        private Bin(TruckSpec truck) {
            this.truck = truck;
        }

        private boolean fits(ShipmentItem shipment) {
            return pallets + shipment.pallets() <= truck.capacityPallets()
                    && weightKg + shipment.weightKg() <= truck.payloadKg();
        }

        private void add(ShipmentItem shipment) {
            shipments.add(shipment);
            pallets += shipment.pallets();
            weightKg += shipment.weightKg();
        }
    }
}
