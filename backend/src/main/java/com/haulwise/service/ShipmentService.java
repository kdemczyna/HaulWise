package com.haulwise.service;

import com.haulwise.domain.Priority;
import com.haulwise.domain.Shipment;
import com.haulwise.domain.Warehouse;
import com.haulwise.dto.ShipmentRequest;
import com.haulwise.dto.ShipmentResponse;
import com.haulwise.exception.FieldViolation;
import com.haulwise.exception.NotFoundException;
import com.haulwise.exception.RequestValidationException;
import com.haulwise.repository.ShipmentRepository;
import com.haulwise.repository.WarehouseRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shipment CRUD. Origin and destination must exist and differ; violations are reported as field
 * errors on {@code originId} / {@code destinationId}. All mapping to DTOs happens inside the
 * transaction because the warehouse associations are lazy and open-in-view is off.
 */
@Service
@Transactional
public class ShipmentService {

    private final ShipmentRepository shipments;
    private final WarehouseRepository warehouses;

    public ShipmentService(ShipmentRepository shipments, WarehouseRepository warehouses) {
        this.shipments = shipments;
        this.warehouses = warehouses;
    }

    /** Shipments filtered by the optional priority and origin ({@code null} = any), ordered by id. */
    @Transactional(readOnly = true)
    public List<ShipmentResponse> search(Priority priority, Long originId) {
        return shipments.search(priority, originId).stream().map(ShipmentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ShipmentResponse findById(Long id) {
        return ShipmentResponse.from(load(id));
    }

    public ShipmentResponse create(ShipmentRequest request) {
        Route route = resolveRoute(request);
        Shipment shipment = new Shipment(request.reference(), route.origin(), route.destination(),
                request.pallets(), request.weightKg());
        applyOptionalFields(shipment, request);
        return ShipmentResponse.from(shipments.save(shipment));
    }

    public ShipmentResponse update(Long id, ShipmentRequest request) {
        Shipment shipment = load(id);
        Route route = resolveRoute(request);
        shipment.setReference(request.reference());
        shipment.setOrigin(route.origin());
        shipment.setDestination(route.destination());
        shipment.setPallets(request.pallets());
        shipment.setWeightKg(request.weightKg());
        applyOptionalFields(shipment, request);
        return ShipmentResponse.from(shipment);
    }

    public void delete(Long id) {
        shipments.delete(load(id));
    }

    private Shipment load(Long id) {
        return shipments.findWithWarehousesById(id).orElseThrow(() -> new NotFoundException("Shipment", id));
    }

    private Route resolveRoute(ShipmentRequest request) {
        List<FieldViolation> violations = new ArrayList<>();
        Optional<Warehouse> origin = warehouses.findById(request.originId());
        Optional<Warehouse> destination = warehouses.findById(request.destinationId());
        if (origin.isEmpty()) {
            violations.add(missingWarehouse("originId", request.originId()));
        }
        if (destination.isEmpty()) {
            violations.add(missingWarehouse("destinationId", request.destinationId()));
        }
        if (request.originId().equals(request.destinationId())) {
            violations.add(new FieldViolation("destinationId", "must differ from originId"));
        }
        if (!violations.isEmpty()) {
            throw new RequestValidationException(violations);
        }
        return new Route(origin.get(), destination.get());
    }

    private static void applyOptionalFields(Shipment shipment, ShipmentRequest request) {
        shipment.setPriority(request.priority());
        shipment.setDeadline(request.deadline());
        shipment.setPinnedFirst(Boolean.TRUE.equals(request.pinnedFirst()));
    }

    private static FieldViolation missingWarehouse(String field, Long id) {
        return new FieldViolation(field, "warehouse " + id + " does not exist");
    }

    private record Route(Warehouse origin, Warehouse destination) {
    }
}
