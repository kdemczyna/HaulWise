package com.haulwise.service;

import com.haulwise.domain.Priority;
import com.haulwise.dto.WarehouseResponse;
import com.haulwise.dto.plan.DeltaResponse;
import com.haulwise.dto.plan.PlanNumbers;
import com.haulwise.dto.plan.PlanRequest;
import com.haulwise.dto.plan.PlanResponse;
import com.haulwise.dto.plan.PlanVariantResponse;
import com.haulwise.dto.plan.ReturnLegResponse;
import com.haulwise.dto.plan.RouteResponse;
import com.haulwise.dto.plan.StopResponse;
import com.haulwise.dto.plan.TotalsResponse;
import com.haulwise.dto.plan.UnassignedResponse;
import com.haulwise.dto.plan.UtilisationResponse;
import com.haulwise.dto.plan.WarningResponse;
import com.haulwise.service.PlanWarnings.LateShipment;
import com.haulwise.solver.model.DirectedPair;
import com.haulwise.solver.model.Leg;
import com.haulwise.solver.model.PlanVariant;
import com.haulwise.solver.model.RoutePlan;
import com.haulwise.solver.model.SolverResult;
import com.haulwise.solver.model.Stop;
import com.haulwise.solver.model.Unassigned;
import com.haulwise.solver.model.Warning;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Maps a {@link SolverResult} to the plan response (ARCHITECTURE section 5): clock times from the departure time
 * plus the solver's minute offsets (rounded to whole minutes), {@code deadlineMissed} per stop with a
 * DEADLINE_MISSED route warning, user-facing warning texts, and all numbers rounded by {@link PlanNumbers}.
 * Works on snapshots taken from the entities, so it needs no persistence session.
 */
final class PlanResponseMapper {

    private final LocalDateTime departure;
    private final Map<Long, WarehouseResponse> warehouses;
    private final Map<Long, ShipmentInfo> shipments;
    private final Map<Long, String> plates;

    /**
     * @param departure departure from the depot
     * @param warehouses the depot and every destination, by id
     * @param shipments every selected shipment, by id
     * @param plates every selected truck's plate, by id
     */
    PlanResponseMapper(LocalDateTime departure, Map<Long, WarehouseResponse> warehouses,
                       Map<Long, ShipmentInfo> shipments, Map<Long, String> plates) {
        this.departure = Objects.requireNonNull(departure, "departure");
        this.warehouses = Map.copyOf(warehouses);
        this.shipments = Map.copyOf(shipments);
        this.plates = Map.copyOf(plates);
    }

    PlanResponse map(UUID id, LocalDateTime createdAt, PlanRequest request, long depotId, SolverResult result) {
        List<WarningResponse> warnings = new ArrayList<>();
        result.warnings().forEach(warning -> warnings.add(pinConflict(warning, result.priority())));
        List<String> estimatedLegs = estimatedLegNames(result);
        if (!estimatedLegs.isEmpty()) {
            warnings.add(PlanWarnings.estimatedDistance(estimatedLegs));
        }
        return new PlanResponse(id, createdAt, request, warehouse(depotId),
                variant(result.optimal()), variant(result.priority()), DeltaResponse.from(result.delta()),
                result.unassigned().stream().map(this::unassigned).toList(), warnings);
    }

    private PlanVariantResponse variant(PlanVariant variant) {
        List<RouteResponse> routes = new ArrayList<>();
        for (int index = 0; index < variant.routes().size(); index++) {
            routes.add(route(index, variant.routes().get(index)));
        }
        int deadlinesMissed = (int) routes.stream()
                .flatMap(route -> route.stops().stream())
                .filter(StopResponse::deadlineMissed)
                .count();
        return new PlanVariantResponse(variant.mode(), TotalsResponse.from(variant.totals()), deadlinesMissed,
                routes, List.of());
    }

    private RouteResponse route(int colorIndex, RoutePlan plan) {
        long truckId = plan.truck().id();
        List<Stop> stops = plan.route().stops();
        List<Leg> legs = plan.route().evaluation().stopLegs();
        List<StopResponse> stopResponses = new ArrayList<>();
        List<WarningResponse> warnings = new ArrayList<>();
        for (int i = 0; i < stops.size(); i++) {
            StopResponse stop = stop(i + 1, stops.get(i), legs.get(i));
            stopResponses.add(stop);
            if (stop.deadlineMissed()) {
                warnings.add(PlanWarnings.deadlineMissed(truckId, plate(truckId), stop.city(), stop.arrivalTime(),
                        lateShipments(stops.get(i), stop.arrivalTime())));
            }
        }
        Leg returnLeg = plan.route().evaluation().returnLeg().orElse(null);
        return new RouteResponse(truckId, plate(truckId), colorIndex, UtilisationResponse.from(plan.load()),
                departure, returnLeg == null ? null : at(returnLeg.arrivalMinutes()), stopResponses,
                returnLeg == null ? null : ReturnLegResponse.from(returnLeg), TotalsResponse.from(plan.totals()),
                warnings);
    }

    private StopResponse stop(int sequence, Stop stop, Leg leg) {
        WarehouseResponse warehouse = warehouse(stop.warehouseId());
        LocalDateTime arrival = at(leg.arrivalMinutes());
        LocalDateTime deadline = stop.earliestDeadline();
        return new StopResponse(sequence, warehouse.id(), warehouse.name(), warehouse.city(), warehouse.lat(),
                warehouse.lon(), stop.shipmentIds(), stop.dropPallets(), stop.dropWeightKg(),
                Priority.valueOf(stop.maxPriority().name()), stop.pinned(),
                PlanNumbers.km(leg.distanceKm()), PlanNumbers.minutes(leg.drivingMinutes()),
                PlanNumbers.litres(leg.litres()), PlanNumbers.pln(leg.costPln()), leg.distance().source(),
                PlanNumbers.kg(leg.loadWeightKg()), arrival, at(leg.arrivalMinutes() + leg.serviceMinutes()),
                deadline, deadline != null && arrival.isAfter(deadline));
    }

    private List<LateShipment> lateShipments(Stop stop, LocalDateTime arrival) {
        return stop.shipmentIds().stream()
                .map(id -> new LateShipment(id, shipment(id).reference(), shipment(id).deadline()))
                .filter(late -> late.deadline() != null && arrival.isAfter(late.deadline()))
                .toList();
    }

    /**
     * The user-facing PIN_CONFLICT: the solver's warning names only ids; the kept pin is the pinned stop of the
     * truck's PRIORITY route.
     */
    private WarningResponse pinConflict(Warning warning, PlanVariant priority) {
        long truckId = warning.truckId();
        String keptCity = priority.routes().stream()
                .filter(route -> route.truck().id() == truckId)
                .flatMap(route -> route.route().stops().stream())
                .filter(Stop::pinned)
                .findFirst()
                .map(stop -> warehouse(stop.warehouseId()).city())
                .orElseThrow(() -> new IllegalStateException("No kept pin on truck " + truckId));
        List<String> references = warning.shipmentIds().stream().map(id -> shipment(id).reference()).toList();
        return PlanWarnings.pinConflict(truckId, plate(truckId), keptCity, references, warning.shipmentIds());
    }

    /** Distinct estimated legs driven in either variant, as "From -> To" warehouse names. */
    private List<String> estimatedLegNames(SolverResult result) {
        Set<DirectedPair> estimated = new LinkedHashSet<>();
        Stream.of(result.optimal(), result.priority())
                .flatMap(variant -> variant.routes().stream())
                .flatMap(route -> route.route().evaluation().allLegs().stream())
                .filter(leg -> leg.distance().isEstimated())
                .forEach(leg -> estimated.add(new DirectedPair(leg.fromId(), leg.toId())));
        return estimated.stream()
                .map(pair -> warehouse(pair.fromId()).name() + " -> " + warehouse(pair.toId()).name())
                .toList();
    }

    private UnassignedResponse unassigned(Unassigned unassigned) {
        return new UnassignedResponse(unassigned.shipmentId(), shipment(unassigned.shipmentId()).reference(),
                unassigned.reason(), unassigned.message());
    }

    private LocalDateTime at(double minutesAfterDeparture) {
        return departure.plusMinutes(PlanNumbers.minutes(minutesAfterDeparture));
    }

    private WarehouseResponse warehouse(long id) {
        return Objects.requireNonNull(warehouses.get(id), () -> "No warehouse " + id);
    }

    private ShipmentInfo shipment(long id) {
        return Objects.requireNonNull(shipments.get(id), () -> "No shipment " + id);
    }

    private String plate(long truckId) {
        return Objects.requireNonNull(plates.get(truckId), () -> "No truck " + truckId);
    }

    /** What the response needs of a shipment beyond the solver's view. */
    record ShipmentInfo(String reference, LocalDateTime deadline) {
    }
}
