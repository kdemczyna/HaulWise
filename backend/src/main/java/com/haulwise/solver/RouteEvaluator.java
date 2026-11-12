package com.haulwise.solver;

import com.haulwise.solver.model.CostParameters;
import com.haulwise.solver.model.DistanceProvider;
import com.haulwise.solver.model.Leg;
import com.haulwise.solver.model.LegDistance;
import com.haulwise.solver.model.RouteEvaluation;
import com.haulwise.solver.model.RouteStop;
import com.haulwise.solver.model.Totals;
import com.haulwise.solver.model.TruckSpec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Fuel, time and cost of an ordered route depot -> stop 1 -> ... -> stop N [-> depot] (ARCHITECTURE 6.2).
 * <ul>
 *   <li>The truck leaves the depot carrying the sum of all drops; the load falls by each stop's drop
 *       after the stop; the return leg is driven empty.</li>
 *   <li>{@code litres = km / 100 x (base + extraFullLoad x loadKg / payloadKg)} (D-04, by weight).</li>
 *   <li>Every stop adds {@code serviceMinutesPerStop}; the depot does not.</li>
 *   <li>{@code fuelCost = litres x dieselPrice}; {@code driverCost = (driving + service minutes) / 60 x
 *       driverCostPerHour}; {@code cost = fuelCost + driverCost}.</li>
 *   <li>Arrival at stop k = sum of leg durations up to k + service x (k - 1), in minutes after departure.</li>
 * </ul>
 * Values are not rounded; minutes are {@code double} throughout the solver. Arrivals are offsets from
 * departure: turning them into clock times and checking deadlines is left to the caller (T-13 Planner).
 * Stateless and thread-safe.
 */
public final class RouteEvaluator {

    private final DistanceProvider distances;
    private final CostParameters parameters;

    public RouteEvaluator(DistanceProvider distances, CostParameters parameters) {
        this.distances = Objects.requireNonNull(distances, "distances");
        this.parameters = Objects.requireNonNull(parameters, "parameters");
    }

    /**
     * @param depotId warehouse the truck starts at (and returns to)
     * @param stops stops in delivery order: distinct warehouses, none of them the depot; their total drop
     *              weight must not exceed the truck's payload
     * @param truck the truck driving the route
     * @param returnToDepot whether to add the empty leg from the last stop back to the depot
     * @throws IllegalArgumentException for an invalid route or a warehouse unknown to the distance provider
     */
    public RouteEvaluation evaluate(long depotId, List<RouteStop> stops, TruckSpec truck, boolean returnToDepot) {
        Objects.requireNonNull(truck, "truck");
        double load = validateAndSumLoad(depotId, stops, truck);

        List<Leg> stopLegs = new ArrayList<>(stops.size());
        Totals totals = Totals.ZERO;
        long position = depotId;
        double clockMinutes = 0;
        for (RouteStop stop : stops) {
            Leg leg = leg(position, stop.warehouseId(), load, clockMinutes, parameters.serviceMinutesPerStop(), truck);
            stopLegs.add(leg);
            totals = totals.plus(leg.totals());
            clockMinutes = leg.arrivalMinutes() + leg.serviceMinutes();
            load -= stop.dropWeightKg();
            position = stop.warehouseId();
        }

        Optional<Leg> returnLeg = Optional.empty();
        if (returnToDepot && !stops.isEmpty()) {
            Leg leg = leg(position, depotId, 0, clockMinutes, 0, truck);
            returnLeg = Optional.of(leg);
            totals = totals.plus(leg.totals());
        }
        return new RouteEvaluation(stopLegs, returnLeg, totals);
    }

    private Leg leg(long fromId, long toId, double loadKg, double departureMinutes, int serviceMinutes,
            TruckSpec truck) {
        LegDistance distance = distances.distance(fromId, toId);
        double litres = distance.distanceKm() / 100 * truck.consumptionLPer100Km(loadKg);
        double workedHours = (distance.durationMinutes() + serviceMinutes) / 60;
        return new Leg(
                fromId,
                toId,
                distance,
                loadKg,
                departureMinutes + distance.durationMinutes(),
                serviceMinutes,
                litres,
                litres * parameters.dieselPricePlnPerLitre(),
                workedHours * truck.driverCostPlnPerHour());
    }

    private static double validateAndSumLoad(long depotId, List<RouteStop> stops, TruckSpec truck) {
        Objects.requireNonNull(stops, "stops");
        Set<Long> visited = new HashSet<>();
        long totalKg = 0;
        for (RouteStop stop : stops) {
            if (stop.warehouseId() == depotId) {
                throw new IllegalArgumentException("Stop " + stop.warehouseId() + " is the depot");
            }
            if (!visited.add(stop.warehouseId())) {
                throw new IllegalArgumentException("Warehouse " + stop.warehouseId() + " appears twice in the route");
            }
            totalKg += stop.dropWeightKg();
        }
        if (totalKg > truck.payloadKg()) {
            throw new IllegalArgumentException("Route load " + totalKg + " kg exceeds payload " + truck.payloadKg()
                    + " kg of truck " + truck.id());
        }
        return totalKg;
    }
}
