package com.haulwise.solver;

import com.haulwise.solver.model.Delta;
import com.haulwise.solver.model.Mode;
import com.haulwise.solver.model.PlanVariant;
import com.haulwise.solver.model.Priority;
import com.haulwise.solver.model.Stop;
import com.haulwise.solver.model.Totals;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The cost of priority (ARCHITECTURE 6.1 step 7, 6.5): PRIORITY totals minus OPTIMAL totals and a
 * plain-language sentence.
 * <ul>
 *   <li>No extra cost when |cost| &lt; {@value #NO_EXTRA_COST_PLN} PLN and |distance| &lt;
 *       {@value #NO_EXTRA_DISTANCE_KM} km: "Respecting priorities costs nothing extra."</li>
 *   <li>Otherwise the cost is the main clause and distance and time follow in words:
 *       "{subject} costs 292.97 PLN more (85 km and 1 h 15 min longer)." With mixed signs:
 *       "(12 km shorter, 40 min longer)"; a component that rounds to 0 is left out ("(40 min longer)"), and
 *       when both do the parentheses are dropped. PLN to 2 decimals, km to whole km, time to whole minutes
 *       ("41 min", "1 h 15 min", "2 h"), all with {@link Rounding#halfUp}; a negative cost (not produced by
 *       the Planner, D-14) reads "less".</li>
 *   <li>Subject: the cities of pinned, URGENT or HIGH stops that the PRIORITY variant visits earlier on
 *       their truck than the OPTIMAL one (a stop pushed later by another stop's pin is not what the extra
 *       cost buys, so it is not named) - pinned first, then by priority, then by truck and position in the
 *       PRIORITY variant; each city named once. When all such stops are pinned and name one city:
 *       "Delivering Gdansk first". Otherwise "Prioritising Lublin", "... Lublin and Gdansk",
 *       "... Lublin, Gdansk and Krakow", at most three cities, then "and N more". No such stop:
 *       "Respecting priorities".</li>
 * </ul>
 * Stateless and thread-safe.
 */
public final class DeltaCalculator {

    static final double NO_EXTRA_COST_PLN = 0.50;
    static final double NO_EXTRA_DISTANCE_KM = 0.5;
    static final String NO_EXTRA_COST_SENTENCE = "Respecting priorities costs nothing extra.";
    static final String FALLBACK_SUBJECT = "Respecting priorities";
    static final int MAX_NAMED_CITIES = 3;

    private static final int MINUTES_PER_HOUR = 60;
    private static final Comparator<MovedStop> SUBJECT_ORDER = Comparator
            .comparing((MovedStop moved) -> !moved.stop().pinned())
            .thenComparing(moved -> moved.stop().maxPriority())
            .thenComparingInt(MovedStop::truckIndex)
            .thenComparingInt(MovedStop::position);

    /**
     * @param optimal the OPTIMAL variant
     * @param priority the PRIORITY variant over the same trucks in the same order (D-03)
     * @param cityNames warehouse id to city, for every stop
     * @throws IllegalArgumentException when the variants are swapped or do not route the same trucks, or a
     *         moved stop has no city name
     */
    public Delta calculate(PlanVariant optimal, PlanVariant priority, Map<Long, String> cityNames) {
        requireComparable(optimal, priority);
        Objects.requireNonNull(cityNames, "cityNames");
        Totals withPriority = priority.totals();
        Totals byCost = optimal.totals();
        double distanceKm = withPriority.distanceKm() - byCost.distanceKm();
        double durationMinutes = withPriority.totalMinutes() - byCost.totalMinutes();
        double costPln = withPriority.costPln() - byCost.costPln();
        boolean noExtraCost = Math.abs(costPln) < NO_EXTRA_COST_PLN && Math.abs(distanceKm) < NO_EXTRA_DISTANCE_KM;
        String sentence = noExtraCost
                ? NO_EXTRA_COST_SENTENCE
                : sentence(subject(optimal, priority, cityNames), distanceKm, costPln, durationMinutes);
        return new Delta(
                distanceKm,
                durationMinutes,
                withPriority.litres() - byCost.litres(),
                withPriority.fuelCostPln() - byCost.fuelCostPln(),
                withPriority.driverCostPln() - byCost.driverCostPln(),
                costPln,
                noExtraCost,
                sentence);
    }

    /** "{subject} costs 292.97 PLN more (85 km and 1 h 15 min longer)." */
    static String sentence(String subject, double distanceKm, double costPln, double durationMinutes) {
        BigDecimal pln = Rounding.halfUp(costPln, 2);
        String cost = subject + " costs " + pln.abs().toPlainString() + " PLN " + (pln.signum() < 0 ? "less" : "more");
        String details = distanceAndTime(Rounding.halfUp(distanceKm, 0), Rounding.halfUp(durationMinutes, 0));
        return cost + (details.isEmpty() ? "" : " (" + details + ")") + ".";
    }

    /**
     * "85 km and 1 h 15 min longer", "12 km shorter, 40 min longer", "40 min longer", or "" when both are 0.
     */
    static String distanceAndTime(BigDecimal km, BigDecimal minutes) {
        String distance = km.signum() == 0 ? null : km.abs().toPlainString() + " km";
        String time = minutes.signum() == 0 ? null : duration(minutes.abs().intValueExact());
        if (distance == null && time == null) {
            return "";
        }
        if (distance == null) {
            return time + " " + longerOrShorter(minutes);
        }
        if (time == null) {
            return distance + " " + longerOrShorter(km);
        }
        if (km.signum() == minutes.signum()) {
            return distance + " and " + time + " " + longerOrShorter(km);
        }
        return distance + " " + longerOrShorter(km) + ", " + time + " " + longerOrShorter(minutes);
    }

    /** Whole minutes: "41 min", "1 h 15 min", "2 h". */
    static String duration(int minutes) {
        int hours = minutes / MINUTES_PER_HOUR;
        int rest = minutes % MINUTES_PER_HOUR;
        if (hours == 0) {
            return rest + " min";
        }
        return rest == 0 ? hours + " h" : hours + " h " + rest + " min";
    }

    private static String longerOrShorter(BigDecimal rounded) {
        return rounded.signum() < 0 ? "shorter" : "longer";
    }

    /** "Gdansk", "Lublin and Gdansk", "Lublin, Gdansk and Krakow", "Lublin, Gdansk, Krakow and 2 more". */
    static String joinCities(List<String> cities) {
        if (cities.isEmpty()) {
            throw new IllegalArgumentException("No cities to join");
        }
        if (cities.size() > MAX_NAMED_CITIES) {
            return String.join(", ", cities.subList(0, MAX_NAMED_CITIES)) + " and "
                    + (cities.size() - MAX_NAMED_CITIES) + " more";
        }
        if (cities.size() == 1) {
            return cities.get(0);
        }
        return String.join(", ", cities.subList(0, cities.size() - 1)) + " and " + cities.get(cities.size() - 1);
    }

    private static String subject(PlanVariant optimal, PlanVariant priority, Map<Long, String> cityNames) {
        List<MovedStop> moved = movedImportantStops(optimal, priority);
        if (moved.isEmpty()) {
            return FALLBACK_SUBJECT;
        }
        LinkedHashSet<String> cities = new LinkedHashSet<>();
        moved.forEach(stop -> cities.add(city(stop.stop(), cityNames)));
        boolean allPinned = moved.stream().allMatch(stop -> stop.stop().pinned());
        if (allPinned && cities.size() == 1) {
            return "Delivering " + cities.iterator().next() + " first";
        }
        return "Prioritising " + joinCities(List.copyOf(cities));
    }

    /** Pinned, URGENT or HIGH stops that PRIORITY visits earlier than OPTIMAL, in {@link #SUBJECT_ORDER}. */
    private static List<MovedStop> movedImportantStops(PlanVariant optimal, PlanVariant priority) {
        List<MovedStop> moved = new ArrayList<>();
        for (int truck = 0; truck < priority.routes().size(); truck++) {
            List<Long> optimalOrder = optimal.routes().get(truck).route().warehouseIds();
            List<Stop> priorityStops = priority.routes().get(truck).route().stops();
            for (int position = 0; position < priorityStops.size(); position++) {
                Stop stop = priorityStops.get(position);
                if (isImportant(stop) && optimalOrder.indexOf(stop.warehouseId()) > position) {
                    moved.add(new MovedStop(stop, truck, position));
                }
            }
        }
        moved.sort(SUBJECT_ORDER);
        return moved;
    }

    private static boolean isImportant(Stop stop) {
        return stop.pinned() || stop.maxPriority() != Priority.NORMAL;
    }

    private static String city(Stop stop, Map<Long, String> cityNames) {
        String city = cityNames.get(stop.warehouseId());
        if (city == null) {
            throw new IllegalArgumentException("No city name for warehouse " + stop.warehouseId());
        }
        return city;
    }

    private static void requireComparable(PlanVariant optimal, PlanVariant priority) {
        Objects.requireNonNull(optimal, "optimal");
        Objects.requireNonNull(priority, "priority");
        if (optimal.mode() != Mode.OPTIMAL || priority.mode() != Mode.PRIORITY) {
            throw new IllegalArgumentException("Expected an OPTIMAL and a PRIORITY variant, got "
                    + optimal.mode() + " and " + priority.mode());
        }
        if (!truckIds(optimal).equals(truckIds(priority))) {
            throw new IllegalArgumentException("Variants must route the same trucks in the same order (D-03): "
                    + truckIds(optimal) + " vs " + truckIds(priority));
        }
    }

    private static List<Long> truckIds(PlanVariant variant) {
        return variant.routes().stream().map(route -> route.truck().id()).toList();
    }

    /** An important stop of the PRIORITY variant that sits elsewhere in the OPTIMAL one. */
    private record MovedStop(Stop stop, int truckIndex, int position) {
    }
}
