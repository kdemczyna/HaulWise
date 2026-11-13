package com.haulwise.service;

import com.haulwise.config.HaulWiseProperties;
import com.haulwise.domain.Shipment;
import com.haulwise.domain.Truck;
import com.haulwise.domain.Warehouse;
import com.haulwise.dto.WarehouseResponse;
import com.haulwise.dto.plan.PlanRequest;
import com.haulwise.dto.plan.PlanResponse;
import com.haulwise.exception.FieldViolation;
import com.haulwise.exception.NotFoundException;
import com.haulwise.exception.RequestValidationException;
import com.haulwise.repository.ShipmentRepository;
import com.haulwise.repository.TruckRepository;
import com.haulwise.repository.WarehouseRepository;
import com.haulwise.service.PlanResponseMapper.ShipmentInfo;
import com.haulwise.solver.Planner;
import com.haulwise.solver.model.Priority;
import com.haulwise.solver.model.ShipmentItem;
import com.haulwise.solver.model.SolverInput;
import com.haulwise.solver.model.SolverResult;
import com.haulwise.solver.model.TruckSpec;
import jakarta.validation.Validator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Plans a run (ARCHITECTURE 6.1, D-05): validates the request, maps the selected entities to solver input, runs
 * the {@link Planner} (both variants), maps the result to the response and keeps it in the {@link PlanStore}.
 * Only loading, validation and building the solver input (matrix, settings) run in a read-write transaction (the
 * settings row may be created); the solver, the mapping and storing run after it has committed.
 * <p>
 * Defaults: {@code returnToDepot} {@value #DEFAULT_RETURN_TO_DEPOT}, departure tomorrow 06:00 (D-10, from the
 * injected clock).
 */
@Service
public class PlanService {

    static final boolean DEFAULT_RETURN_TO_DEPOT = true;
    static final LocalTime DEFAULT_DEPARTURE = LocalTime.of(6, 0);

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);

    private final WarehouseRepository warehouses;
    private final ShipmentRepository shipments;
    private final TruckRepository trucks;
    private final DistanceService distanceService;
    private final SettingsService settingsService;
    private final PlanStore planStore;
    private final Validator validator;
    private final Clock clock;
    private final Planner planner;
    private final TransactionTemplate transaction;

    public PlanService(WarehouseRepository warehouses, ShipmentRepository shipments, TruckRepository trucks,
                       DistanceService distanceService, SettingsService settingsService, PlanStore planStore,
                       Validator validator, Clock clock, HaulWiseProperties properties,
                       PlatformTransactionManager transactionManager) {
        this.warehouses = warehouses;
        this.shipments = shipments;
        this.trucks = trucks;
        this.distanceService = distanceService;
        this.settingsService = settingsService;
        this.planStore = planStore;
        this.validator = validator;
        this.clock = clock;
        this.planner = new Planner(properties.solver().maxBruteForceStops());
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Plans the selected shipments onto the selected trucks and stores the plan.
     *
     * @throws RequestValidationException for invalid fields, unknown or duplicate ids, shipments that do not
     *         start at the depot, or trucks based elsewhere or on route - all reported together
     */
    public PlanResponse create(PlanRequest request) {
        long started = System.nanoTime();
        Prepared prepared = Objects.requireNonNull(transaction.execute(status -> prepare(request)));
        SolverResult result = planner.plan(prepared.input());
        PlanResponse plan = prepared.mapper().map(UUID.randomUUID(),
                LocalDateTime.now(clock).truncatedTo(ChronoUnit.SECONDS), prepared.request(),
                prepared.input().depotId(), result);
        planStore.save(plan);
        log.info("Plan solved: planId={}, shipments={}, trucks={}, ms={}", plan.id(),
                prepared.input().shipments().size(), prepared.input().trucks().size(),
                (System.nanoTime() - started) / 1_000_000);
        return plan;
    }

    /** Validates the request and snapshots everything the solver and the mapper need; runs in the transaction. */
    private Prepared prepare(PlanRequest request) {
        Selection selection = validate(request);
        boolean returnToDepot = request.returnToDepot() == null ? DEFAULT_RETURN_TO_DEPOT : request.returnToDepot();
        LocalDateTime departure = request.departureTime() != null
                ? request.departureTime()
                : LocalDate.now(clock).plusDays(1).atTime(DEFAULT_DEPARTURE);
        return new Prepared(solverInput(selection, returnToDepot), mapper(selection, departure),
                request.resolved(returnToDepot, departure));
    }

    /**
     * @throws NotFoundException when no plan has this id (never created, evicted, or lost on restart)
     */
    public PlanResponse get(String id) {
        return planStore.find(id).orElseThrow(() -> new NotFoundException("Plan", id));
    }

    // --- validation -------------------------------------------------------------------------------------------

    /** Checks the request before anything touches the distance matrix (which rejects unknown ids). */
    private Selection validate(PlanRequest request) {
        List<FieldViolation> violations = new ArrayList<>(validator.validate(request).stream()
                .map(v -> new FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList());
        Warehouse depot = findDepot(request.depotId(), violations);
        List<Shipment> selectedShipments = findAll("shipmentIds", "shipment", request.shipmentIds(),
                shipments::findWithWarehousesByIdIn, Shipment::getId, violations);
        if (depot != null) {
            checkOrigins(depot, selectedShipments, violations);
        }
        List<Truck> selectedTrucks = findAll("truckIds", "truck", request.truckIds(),
                trucks::findWithHomeWarehouseByIdIn, Truck::getId, violations);
        if (depot != null) {
            checkTrucks(depot, selectedTrucks, violations);
        }
        if (!violations.isEmpty()) {
            throw new RequestValidationException(violations);
        }
        return new Selection(depot, selectedShipments, selectedTrucks);
    }

    private Warehouse findDepot(Long depotId, List<FieldViolation> violations) {
        if (depotId == null) {
            return null;
        }
        Warehouse depot = warehouses.findById(depotId).orElse(null);
        if (depot == null) {
            violations.add(new FieldViolation("depotId", "warehouse " + depotId + " does not exist"));
        }
        return depot;
    }

    /**
     * Loads the entities for a list of ids in request order. Null, duplicate and unknown ids are field errors;
     * an empty or oversized list is already reported by Bean Validation. Returns an empty list on any error.
     */
    private static <T> List<T> findAll(String field, String noun, List<Long> ids,
                                       Function<Collection<Long>, List<T>> loader, Function<T, Long> idOf,
                                       List<FieldViolation> violations) {
        if (ids == null || ids.isEmpty() || ids.size() > PlanRequest.MAX_IDS) {
            return List.of();
        }
        if (ids.stream().anyMatch(Objects::isNull)) {
            violations.add(new FieldViolation(field, "must not contain null ids"));
            return List.of();
        }
        List<Long> duplicates = duplicates(ids);
        if (!duplicates.isEmpty()) {
            violations.add(new FieldViolation(field, "must not contain duplicate ids: " + joinIds(duplicates)));
            return List.of();
        }
        Map<Long, T> found = loader.apply(ids).stream().collect(Collectors.toMap(idOf, Function.identity()));
        List<Long> missing = ids.stream().filter(id -> !found.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            violations.add(new FieldViolation(field, missing.size() == 1
                    ? noun + " " + missing.get(0) + " does not exist"
                    : noun + "s " + joinIds(missing) + " do not exist"));
            return List.of();
        }
        return ids.stream().map(found::get).toList();
    }

    /** Every shipment of a run starts at the depot (one depot per run, PLAN section 2). */
    private static void checkOrigins(Warehouse depot, List<Shipment> selected, List<FieldViolation> violations) {
        List<String> offenders = selected.stream()
                .filter(shipment -> !shipment.getOrigin().getId().equals(depot.getId()))
                .map(shipment -> shipment.getReference() + " (id " + shipment.getId() + ")")
                .toList();
        if (!offenders.isEmpty()) {
            violations.add(new FieldViolation("shipmentIds", PlanWarnings.joinWithAnd(offenders)
                    + (offenders.size() == 1 ? " does" : " do") + " not start at depot " + depot.getName()));
        }
    }

    /**
     * Every selected truck is based at the depot (T-32) and not on route (T-33); one violation per offending truck
     * and reason, so the dispatcher sees exactly which ticks to undo.
     */
    private static void checkTrucks(Warehouse depot, List<Truck> selected, List<FieldViolation> violations) {
        for (Truck truck : selected) {
            Warehouse home = truck.getHomeWarehouse();
            if (!home.getId().equals(depot.getId())) {
                violations.add(new FieldViolation("truckIds", "Truck %s is based at %s, not at %s."
                        .formatted(truck.getPlate(), home.getName(), depot.getName())));
            }
            if (!truck.isAvailable()) {
                violations.add(new FieldViolation("truckIds",
                        "Truck %s is already on route.".formatted(truck.getPlate())));
            }
        }
    }

    private static List<Long> duplicates(List<Long> ids) {
        Set<Long> seen = new HashSet<>();
        return ids.stream().filter(id -> !seen.add(id)).distinct().toList();
    }

    private static String joinIds(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    // --- mapping ----------------------------------------------------------------------------------------------

    private SolverInput solverInput(Selection selection, boolean returnToDepot) {
        Map<Long, Warehouse> stops = selection.warehouses();
        Map<Long, String> cityNames = stops.values().stream()
                .collect(Collectors.toMap(Warehouse::getId, Warehouse::getCity));
        return new SolverInput(
                selection.depot().getId(),
                selection.shipments().stream().map(PlanService::toItem).toList(),
                selection.trucks().stream().map(PlanService::toSpec).toList(),
                returnToDepot,
                distanceService.buildMatrix(stops.keySet()),
                settingsService.costParameters(),
                settingsService.priorityWeights(),
                cityNames);
    }

    private static PlanResponseMapper mapper(Selection selection, LocalDateTime departure) {
        Map<Long, WarehouseResponse> warehouseViews = selection.warehouses().values().stream()
                .collect(Collectors.toMap(Warehouse::getId, WarehouseResponse::from));
        Map<Long, ShipmentInfo> shipmentInfos = selection.shipments().stream()
                .collect(Collectors.toMap(Shipment::getId,
                        shipment -> new ShipmentInfo(shipment.getReference(), shipment.getDeadline())));
        Map<Long, String> plates = selection.trucks().stream()
                .collect(Collectors.toMap(Truck::getId, Truck::getPlate));
        return new PlanResponseMapper(departure, warehouseViews, shipmentInfos, plates);
    }

    private static ShipmentItem toItem(Shipment shipment) {
        return new ShipmentItem(shipment.getId(), shipment.getDestination().getId(), shipment.getPallets(),
                shipment.getWeightKg(), Priority.valueOf(shipment.getPriority().name()), shipment.isPinnedFirst(),
                shipment.getDeadline());
    }

    private static TruckSpec toSpec(Truck truck) {
        return new TruckSpec(truck.getId(), truck.getCapacityPallets(), truck.getPayloadKg(),
                truck.getBaseConsumptionLPer100Km(), truck.getExtraConsumptionFullLoadLPer100Km(),
                truck.getDriverCostPlnPerHour());
    }

    /** Solver input, response mapper and resolved request of one plan; no entities, safe after the transaction. */
    private record Prepared(SolverInput input, PlanResponseMapper mapper, PlanRequest request) {
    }

    /** The validated entities of a request. */
    private record Selection(Warehouse depot, List<Shipment> shipments, List<Truck> trucks) {

        /** The depot and every destination, by id, depot first. */
        Map<Long, Warehouse> warehouses() {
            Map<Long, Warehouse> byId = new LinkedHashMap<>();
            byId.put(depot.getId(), depot);
            shipments.forEach(shipment -> byId.putIfAbsent(shipment.getDestination().getId(),
                    shipment.getDestination()));
            return byId;
        }
    }
}
