package com.haulwise.service;

import com.haulwise.config.HaulWiseProperties;
import com.haulwise.domain.Shipment;
import com.haulwise.domain.Truck;
import com.haulwise.domain.Warehouse;
import com.haulwise.dto.RecommendedTruckResponse;
import com.haulwise.dto.TruckRecommendationRequest;
import com.haulwise.dto.TruckRecommendationResponse;
import com.haulwise.dto.plan.PlanNumbers;
import com.haulwise.dto.plan.PlanRequest;
import com.haulwise.dto.plan.TotalsResponse;
import com.haulwise.dto.plan.UnassignedResponse;
import com.haulwise.exception.FieldViolation;
import com.haulwise.exception.RequestValidationException;
import com.haulwise.repository.ShipmentRepository;
import com.haulwise.repository.TruckRepository;
import com.haulwise.repository.WarehouseRepository;
import com.haulwise.solver.BinPacker;
import com.haulwise.solver.RouteObjective;
import com.haulwise.solver.StopGrouper;
import com.haulwise.solver.StopSequencer;
import com.haulwise.solver.model.CostParameters;
import com.haulwise.solver.model.DistanceProvider;
import com.haulwise.solver.model.PackingResult;
import com.haulwise.solver.model.Priority;
import com.haulwise.solver.model.SequencedRoute;
import com.haulwise.solver.model.ShipmentItem;
import com.haulwise.solver.model.Stop;
import com.haulwise.solver.model.Totals;
import com.haulwise.solver.model.TruckLoad;
import com.haulwise.solver.model.TruckSpec;
import com.haulwise.solver.model.Unassigned;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recommends which trucks of the <em>whole fleet</em> to use for a depot + shipment selection (ARCHITECTURE
 * section 5, T-30): the cheapest combination that carries everything, considering the full fleet rather than
 * only the trucks the dispatcher already ticked.
 * <p>
 * Validation mirrors {@link PlanService} (ids exist, shipments start at the depot, 1..200 ids), minus the
 * truck selection - here every truck is a candidate.
 * <p>
 * Search (BinPacker shares the assignment semantics with {@link com.haulwise.solver.Planner}, D-03):
 * <ul>
 *   <li>Up to {@code haulwise.solver.max-recommendation-trucks} trucks: every non-empty subset is tried,
 *       smallest first; a subset is feasible when {@link BinPacker} leaves nothing unassigned. The cheapest
 *       feasible subset wins (cost by {@link StopSequencer} with {@link RouteObjective#COST}, OPTIMAL mode,
 *       compared at {@link StopSequencer#SCORE_RESOLUTION}) - even when it uses more trucks than another
 *       feasible subset; only equal costs are broken by fewer trucks, then the lowest truck ids.</li>
 *   <li>Above the cap: packing all trucks, then greedily dropping any truck whose shipments the rest can
 *       absorb without leaving anything unassigned (documented limitation, not a bug - the result may not be
 *       the cheapest possible combination).</li>
 * </ul>
 * If no subset - not even the whole fleet - can carry everything, the response names the shipments that do
 * not fit anywhere instead of failing.
 */
@Service
public class TruckRecommendationService {

    private final WarehouseRepository warehouses;
    private final ShipmentRepository shipments;
    private final TruckRepository trucks;
    private final DistanceService distanceService;
    private final SettingsService settingsService;
    private final Validator validator;
    private final int maxBruteForceStops;
    private final int maxRecommendationTrucks;
    private final BinPacker packer = new BinPacker();
    private final StopGrouper grouper = new StopGrouper();

    public TruckRecommendationService(WarehouseRepository warehouses, ShipmentRepository shipments,
                                      TruckRepository trucks, DistanceService distanceService,
                                      SettingsService settingsService, Validator validator,
                                      HaulWiseProperties properties) {
        this.warehouses = warehouses;
        this.shipments = shipments;
        this.trucks = trucks;
        this.distanceService = distanceService;
        this.settingsService = settingsService;
        this.validator = validator;
        this.maxBruteForceStops = properties.solver().maxBruteForceStops();
        this.maxRecommendationTrucks = properties.solver().maxRecommendationTrucks();
    }

    @Transactional
    public TruckRecommendationResponse recommend(TruckRecommendationRequest request) {
        Selection selection = validate(request);
        boolean returnToDepot = request.returnToDepot() == null || request.returnToDepot();
        List<ShipmentItem> items = selection.shipments().stream().map(TruckRecommendationService::toItem).toList();
        List<TruckSpec> fleet = trucks.findAll().stream().map(TruckRecommendationService::toSpec).toList();

        // Every request has at least one shipment (validated), so an empty fleet always leaves it unassigned
        // ("No trucks are selected.") and is reported as infeasible rather than needing a separate case.
        PackingResult fullFleetPacking = packer.pack(items, fleet);
        if (!fullFleetPacking.unassigned().isEmpty()) {
            return infeasible(fullFleetPacking.unassigned(), selection);
        }

        Map<Long, Warehouse> stopWarehouses = selection.warehouses();
        DistanceProvider distances = distanceService.buildMatrix(stopWarehouses.keySet());
        CostParameters cost = settingsService.costParameters();
        StopSequencer sequencer = new StopSequencer(distances, cost, maxBruteForceStops);

        long depotId = selection.depot().getId();
        List<TruckSpec> combo = fleet.size() <= maxRecommendationTrucks
                ? bruteForceSearch(items, fleet, depotId, returnToDepot, sequencer)
                : fallbackSearch(items, fleet);
        return response(combo, items, depotId, returnToDepot, sequencer);
    }

    // --- search ------------------------------------------------------------------------------------------------

    /** Every non-empty subset of the fleet, smallest first; the cheapest feasible one wins. */
    private List<TruckSpec> bruteForceSearch(List<ShipmentItem> items, List<TruckSpec> fleet, long depotId,
                                             boolean returnToDepot, StopSequencer sequencer) {
        int n = fleet.size();
        List<TruckSpec> best = null;
        double bestCost = Double.POSITIVE_INFINITY;
        for (int mask = 1; mask < (1 << n); mask++) {
            List<TruckSpec> subset = subset(fleet, mask);
            PackingResult packing = packer.pack(items, subset);
            if (!packing.unassigned().isEmpty()) {
                continue;
            }
            double subsetCost = totalCost(packing, depotId, returnToDepot, sequencer);
            if (best == null || isBetter(subset, subsetCost, best, bestCost)) {
                best = subset;
                bestCost = subsetCost;
            }
        }
        // The full fleet is already known feasible (checked by the caller), so a result always exists.
        return Objects.requireNonNull(best, "no feasible subset found although the full fleet is feasible");
    }

    /**
     * Lower total cost first (compared at {@link StopSequencer#SCORE_RESOLUTION}, like route scores), then fewer
     * trucks, then the lexicographically lower sorted truck-id sequence.
     */
    private static boolean isBetter(List<TruckSpec> candidate, double candidateCost, List<TruckSpec> best,
                                     double bestCost) {
        int byCost = Double.compare(rounded(candidateCost), rounded(bestCost));
        if (byCost != 0) {
            return byCost < 0;
        }
        if (candidate.size() != best.size()) {
            return candidate.size() < best.size();
        }
        List<Long> candidateIds = sortedIds(candidate);
        List<Long> bestIds = sortedIds(best);
        for (int i = 0; i < candidateIds.size(); i++) {
            int byId = Long.compare(candidateIds.get(i), bestIds.get(i));
            if (byId != 0) {
                return byId < 0;
            }
        }
        return false;
    }

    private static double rounded(double costPln) {
        return Math.rint(costPln / StopSequencer.SCORE_RESOLUTION);
    }

    private static List<TruckSpec> subset(List<TruckSpec> fleet, int mask) {
        List<TruckSpec> subset = new ArrayList<>();
        for (int i = 0; i < fleet.size(); i++) {
            if ((mask & (1 << i)) != 0) {
                subset.add(fleet.get(i));
            }
        }
        return subset;
    }

    /**
     * Above the cap: pack with the whole fleet, then drop trucks whose shipments the rest can absorb without
     * creating unassigned ones, until no more can be dropped (documented limitation: not guaranteed optimal).
     */
    private List<TruckSpec> fallbackSearch(List<ShipmentItem> items, List<TruckSpec> fleet) {
        List<TruckSpec> current = packer.pack(items, fleet).loads().stream().map(TruckLoad::truck).toList();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (TruckSpec candidate : current) {
                List<TruckSpec> without = current.stream().filter(t -> t.id() != candidate.id()).toList();
                if (without.size() < current.size() && packer.pack(items, without).unassigned().isEmpty()) {
                    current = without;
                    changed = true;
                    break;
                }
            }
        }
        return current;
    }

    private double totalCost(PackingResult packing, long depotId, boolean returnToDepot, StopSequencer sequencer) {
        double cost = 0;
        for (TruckLoad load : packing.loads()) {
            cost += routeOf(load, depotId, returnToDepot, sequencer).evaluation().totals().costPln();
        }
        return cost;
    }

    private SequencedRoute routeOf(TruckLoad load, long depotId, boolean returnToDepot, StopSequencer sequencer) {
        List<Stop> stops = grouper.group(load);
        return sequencer.sequence(depotId, stops, load.truck(), returnToDepot, RouteObjective.COST);
    }

    // --- response ------------------------------------------------------------------------------------------------

    private TruckRecommendationResponse response(List<TruckSpec> combo, List<ShipmentItem> items, long depotId,
                                                  boolean returnToDepot, StopSequencer sequencer) {
        PackingResult packing = packer.pack(items, combo);
        Totals totals = Totals.ZERO;
        double palletsPctSum = 0;
        for (TruckLoad load : packing.loads()) {
            totals = totals.plus(routeOf(load, depotId, returnToDepot, sequencer).evaluation().totals());
            palletsPctSum += load.palletsPct();
        }
        double averagePalletsPct = packing.loads().isEmpty() ? 0 : palletsPctSum / packing.loads().size();

        List<Long> truckIds = sortedIds(combo);
        Map<Long, Truck> trucksById = trucks.findAllById(truckIds).stream()
                .collect(Collectors.toMap(Truck::getId, Function.identity()));
        List<RecommendedTruckResponse> truckResponses = truckIds.stream()
                .map(id -> RecommendedTruckResponse.from(trucksById.get(id)))
                .toList();
        return new TruckRecommendationResponse(truckIds, truckResponses, TotalsResponse.from(totals),
                PlanNumbers.percent(averagePalletsPct), List.of());
    }

    private TruckRecommendationResponse infeasible(List<Unassigned> unassigned, Selection selection) {
        List<UnassignedResponse> mapped = unassigned.stream()
                .map(u -> new UnassignedResponse(u.shipmentId(), selection.reference(u.shipmentId()), u.reason(),
                        u.message()))
                .toList();
        return TruckRecommendationResponse.infeasible(mapped);
    }

    private static List<Long> sortedIds(List<TruckSpec> trucks) {
        return trucks.stream().map(TruckSpec::id).sorted().toList();
    }

    // --- validation (mirrors PlanService.validate, minus truck selection) ----------------------------------------

    private Selection validate(TruckRecommendationRequest request) {
        List<FieldViolation> violations = new ArrayList<>(validator.validate(request).stream()
                .map(v -> new FieldViolation(v.getPropertyPath().toString(), v.getMessage()))
                .toList());
        Warehouse depot = findDepot(request.depotId(), violations);
        List<Shipment> selected = findShipments(request.shipmentIds(), violations);
        if (depot != null) {
            checkOrigins(depot, selected, violations);
        }
        if (!violations.isEmpty()) {
            throw new RequestValidationException(violations);
        }
        return new Selection(depot, selected);
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

    private List<Shipment> findShipments(List<Long> ids, List<FieldViolation> violations) {
        String field = "shipmentIds";
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
        Map<Long, Shipment> found = shipments.findWithWarehousesByIdIn(ids).stream()
                .collect(Collectors.toMap(Shipment::getId, Function.identity()));
        List<Long> missing = ids.stream().filter(id -> !found.containsKey(id)).toList();
        if (!missing.isEmpty()) {
            violations.add(new FieldViolation(field, missing.size() == 1
                    ? "shipment " + missing.get(0) + " does not exist"
                    : "shipments " + joinIds(missing) + " do not exist"));
            return List.of();
        }
        return ids.stream().map(found::get).toList();
    }

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

    private static List<Long> duplicates(List<Long> ids) {
        Set<Long> seen = new HashSet<>();
        return ids.stream().filter(id -> !seen.add(id)).distinct().toList();
    }

    private static String joinIds(List<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    // --- mapping -------------------------------------------------------------------------------------------------

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

    /** The validated depot and shipments of a request; every truck in the repository is a candidate. */
    private record Selection(Warehouse depot, List<Shipment> shipments) {

        /** The depot and every destination, by id, depot first. */
        Map<Long, Warehouse> warehouses() {
            Map<Long, Warehouse> byId = new LinkedHashMap<>();
            byId.put(depot.getId(), depot);
            shipments.forEach(shipment -> byId.putIfAbsent(shipment.getDestination().getId(),
                    shipment.getDestination()));
            return byId;
        }

        String reference(long shipmentId) {
            return shipments.stream()
                    .filter(shipment -> shipment.getId() == shipmentId)
                    .map(Shipment::getReference)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No shipment " + shipmentId));
        }
    }
}
