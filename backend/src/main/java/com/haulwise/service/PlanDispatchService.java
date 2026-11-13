package com.haulwise.service;

import com.haulwise.domain.Shipment;
import com.haulwise.domain.Truck;
import com.haulwise.dto.plan.DispatchRequest;
import com.haulwise.dto.plan.DispatchResponse;
import com.haulwise.dto.plan.DispatchResponse.DispatchedTruck;
import com.haulwise.dto.plan.DispatchResponse.RemovedShipment;
import com.haulwise.dto.plan.PlanResponse;
import com.haulwise.dto.plan.PlanVariantResponse;
import com.haulwise.dto.plan.RouteResponse;
import com.haulwise.exception.ConflictException;
import com.haulwise.exception.NotFoundException;
import com.haulwise.repository.ShipmentRepository;
import com.haulwise.repository.TruckRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Sends a stored plan (T-33, US-17): for the chosen variant, every truck that got a route is marked unavailable
 * (on route) and every shipment carried on those routes is deleted; selected trucks without a route and unassigned
 * shipments are untouched. The plan then carries {@code dispatchedAt}.
 * <p>
 * Refused with 409, changing nothing, when the plan was already sent, has no routes, or is out of date (a routed
 * truck was deleted or is already on route, or a routed shipment no longer exists - e.g. another plan of the same
 * shipments was sent first). Dispatches are serialised ({@code synchronized}), so the same plan cannot be sent
 * twice concurrently; the database changes run in one transaction.
 */
@Service
public class PlanDispatchService {

    private static final Logger log = LoggerFactory.getLogger(PlanDispatchService.class);

    private static final DateTimeFormatter SENT_AT =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    private final PlanStore planStore;
    private final TruckRepository trucks;
    private final ShipmentRepository shipments;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public PlanDispatchService(PlanStore planStore, TruckRepository trucks, ShipmentRepository shipments,
                               Clock clock, PlatformTransactionManager transactionManager) {
        this.planStore = planStore;
        this.trucks = trucks;
        this.shipments = shipments;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * @throws NotFoundException when no plan has this id (never created, evicted, or lost on restart)
     * @throws ConflictException when the plan was already sent, has no routes or is out of date
     */
    public synchronized DispatchResponse dispatch(String planId, DispatchRequest request) {
        PlanResponse plan = planStore.find(planId).orElseThrow(() -> new NotFoundException("Plan", planId));
        if (plan.dispatchedAt() != null) {
            throw new ConflictException("This plan was already sent on " + SENT_AT.format(plan.dispatchedAt()) + ".");
        }
        PlanVariantResponse variant = plan.variant(request.mode());
        if (variant.routes().isEmpty()) {
            throw new ConflictException("This plan has no routes, so there are no trucks to send.");
        }
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS);
        DispatchResponse response = Objects.requireNonNull(
                transaction.execute(status -> apply(plan.id(), request, variant.routes(), now)));
        planStore.markDispatched(planId, now);
        log.info("Plan dispatched: planId={}, mode={}, trucks={}, shipments={}", plan.id(), request.mode(),
                response.trucks().size(), response.removedShipments().size());
        return response;
    }

    /** Checks the routed trucks and shipments are still there and free, then marks and deletes them. */
    private DispatchResponse apply(UUID planId, DispatchRequest request, List<RouteResponse> routes,
                                   LocalDateTime now) {
        List<Long> truckIds = routes.stream().map(RouteResponse::truckId).toList();
        List<Long> shipmentIds = routes.stream()
                .flatMap(route -> route.stops().stream())
                .flatMap(stop -> stop.shipmentIds().stream())
                .toList();
        Map<Long, Truck> trucksById = byId(trucks.findAllById(truckIds), Truck::getId);
        Map<Long, Shipment> shipmentsById = byId(shipments.findAllById(shipmentIds), Shipment::getId);
        checkStillFree(routes, trucksById, shipmentIds, shipmentsById);

        List<DispatchedTruck> sentTrucks = new ArrayList<>();
        for (Long id : truckIds) {
            Truck truck = trucksById.get(id);
            truck.setAvailable(false);
            sentTrucks.add(new DispatchedTruck(id, truck.getPlate()));
        }
        List<RemovedShipment> removed = shipmentIds.stream()
                .map(id -> new RemovedShipment(id, shipmentsById.get(id).getReference()))
                .toList();
        shipments.deleteAll(shipmentsById.values());
        return new DispatchResponse(planId, request.mode(), now, sentTrucks, removed);
    }

    private static void checkStillFree(List<RouteResponse> routes, Map<Long, Truck> trucksById,
                                       List<Long> shipmentIds, Map<Long, Shipment> shipmentsById) {
        List<String> problems = new ArrayList<>();
        for (RouteResponse route : routes) {
            Truck truck = trucksById.get(route.truckId());
            if (truck == null) {
                problems.add("truck " + route.plate() + " no longer exists");
            } else if (!truck.isAvailable()) {
                problems.add("truck " + truck.getPlate() + " is already on route");
            }
        }
        long missingShipments = shipmentIds.stream().filter(id -> !shipmentsById.containsKey(id)).count();
        if (missingShipments > 0) {
            problems.add(missingShipments == 1
                    ? "1 of its shipments no longer exists"
                    : missingShipments + " of its shipments no longer exist");
        }
        if (!problems.isEmpty()) {
            throw new ConflictException("This plan is out of date: " + PlanWarnings.joinWithAnd(problems)
                    + ". Plan the run again.");
        }
    }

    private static <T> Map<Long, T> byId(List<T> entities, Function<T, Long> idOf) {
        return entities.stream().collect(Collectors.toMap(idOf, Function.identity()));
    }
}
