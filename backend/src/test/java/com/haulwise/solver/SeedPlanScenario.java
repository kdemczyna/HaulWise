package com.haulwise.solver;

import com.haulwise.solver.model.CostParameters;
import com.haulwise.solver.model.DirectedPair;
import com.haulwise.solver.model.DistanceMatrix;
import com.haulwise.solver.model.DistanceSource;
import com.haulwise.solver.model.LegDistance;
import com.haulwise.solver.model.Priority;
import com.haulwise.solver.model.PriorityWeights;
import com.haulwise.solver.model.ShipmentItem;
import com.haulwise.solver.model.SolverInput;
import com.haulwise.solver.model.TruckSpec;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The WAW demo of the seed data (ARCHITECTURE 4.1) as plain numbers: warehouses numbered in
 * {@code seed/warehouses.csv} order, shipment ids = PO numbers, trucks 1..5 in {@code seed/trucks.csv} order,
 * the {@code seed/matrix.csv} legs (SEEDED, same both ways) and the section 6.4 default settings.
 * It mirrors the seed CSVs by hand; the T-13 end-to-end test on the loaded seed guards against drift.
 */
final class SeedPlanScenario {

    static final long WAW = 1;
    static final long KRK = 2;
    static final long GDN = 3;
    static final long POZ = 4;
    static final long WRO = 5;
    static final long LDZ = 6;
    static final long KTW = 7;
    static final long LUB = 8;
    static final long SZZ = 9;

    static final Map<Long, String> CITIES = Map.of(WAW, "Warsaw", KRK, "Krakow", GDN, "Gdansk", POZ, "Poznan",
            WRO, "Wroclaw", LDZ, "Lodz", KTW, "Katowice", LUB, "Lublin", SZZ, "Szczecin");

    /** The day after "today"; seed deadlines are relative to it. */
    private static final LocalDateTime TOMORROW = LocalDateTime.of(2026, 11, 13, 0, 0);

    private SeedPlanScenario() {
    }

    static SolverInput input(boolean returnToDepot) {
        return new SolverInput(WAW, shipments(), trucks(), returnToDepot, matrix(), new CostParameters(6.20, 30),
                new PriorityWeights(150, 50), CITIES);
    }

    static List<ShipmentItem> shipments() {
        return List.of(
                shipment(1041, LUB, 6, 3_600, Priority.URGENT, false, TOMORROW.withHour(20)),
                shipment(1042, GDN, 10, 6_500, Priority.HIGH, true, null),
                shipment(1043, KRK, 6, 4_100, Priority.HIGH, false, TOMORROW.plusDays(1).withHour(12)),
                shipment(1044, POZ, 16, 11_000, Priority.NORMAL, false, null),
                shipment(1045, KRK, 14, 9_800, Priority.NORMAL, false, null),
                shipment(1046, LDZ, 12, 7_400, Priority.NORMAL, false, TOMORROW.withHour(10)),
                shipment(1047, KTW, 8, 5_200, Priority.NORMAL, false, null),
                shipment(1048, SZZ, 5, 3_100, Priority.NORMAL, false, TOMORROW.plusDays(1).withHour(16)),
                shipment(1049, WRO, 4, 2_600, Priority.NORMAL, false, null),
                shipment(1050, GDN, 2, 800, Priority.NORMAL, false, null),
                shipment(1099, WRO, 36, 21_000, Priority.NORMAL, false, null));
    }

    static List<TruckSpec> trucks() {
        return List.of(
                new TruckSpec(1, 33, 24_000, 28, 10, 45),
                new TruckSpec(2, 33, 24_000, 29, 10, 45),
                new TruckSpec(3, 18, 12_000, 22, 7, 42),
                new TruckSpec(4, 10, 6_000, 18, 5, 40),
                new TruckSpec(5, 33, 24_000, 27, 10, 45));
    }

    /** {@code seed/matrix.csv}: km / minutes per unordered pair. */
    static DistanceMatrix matrix() {
        Map<DirectedPair, LegDistance> legs = new HashMap<>();
        double[][] rows = {
                {WAW, KRK, 295, 253}, {WAW, GDN, 340, 291}, {WAW, POZ, 310, 266}, {WAW, WRO, 345, 296},
                {WAW, LDZ, 135, 116}, {WAW, KTW, 290, 249}, {WAW, LUB, 170, 146}, {WAW, SZZ, 565, 470},
                {KRK, GDN, 590, 506}, {KRK, POZ, 455, 390}, {KRK, WRO, 270, 231}, {KRK, LDZ, 275, 236},
                {KRK, KTW, 80, 69}, {KRK, LUB, 270, 270}, {KRK, SZZ, 640, 548},
                {GDN, POZ, 315, 270}, {GDN, WRO, 485, 416}, {GDN, LDZ, 340, 291}, {GDN, KTW, 540, 463},
                {GDN, LUB, 505, 435}, {GDN, SZZ, 350, 350},
                {POZ, WRO, 180, 154}, {POZ, LDZ, 215, 184}, {POZ, KTW, 375, 322}, {POZ, LUB, 475, 410},
                {POZ, SZZ, 240, 206},
                {WRO, LDZ, 220, 189}, {WRO, KTW, 200, 171}, {WRO, LUB, 440, 440}, {WRO, SZZ, 370, 317},
                {LDZ, KTW, 200, 171}, {LDZ, LUB, 250, 250}, {LDZ, SZZ, 430, 369},
                {KTW, LUB, 320, 320}, {KTW, SZZ, 560, 480},
                {LUB, SZZ, 700, 600}};
        for (double[] row : rows) {
            LegDistance leg = new LegDistance(row[2], row[3], DistanceSource.SEEDED);
            legs.put(new DirectedPair((long) row[0], (long) row[1]), leg);
            legs.put(new DirectedPair((long) row[1], (long) row[0]), leg);
        }
        return new DistanceMatrix(List.of(WAW, KRK, GDN, POZ, WRO, LDZ, KTW, LUB, SZZ), legs);
    }

    private static ShipmentItem shipment(long id, long destination, int pallets, int weightKg, Priority priority,
            boolean pinned, LocalDateTime deadline) {
        return new ShipmentItem(id, destination, pallets, weightKg, priority, pinned, deadline);
    }
}
