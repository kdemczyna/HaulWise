package com.haulwise.service;

import com.haulwise.domain.Warehouse;
import com.haulwise.dto.WarehouseRequest;
import com.haulwise.dto.WarehouseResponse;
import com.haulwise.exception.ConflictException;
import com.haulwise.exception.NotFoundException;
import com.haulwise.repository.RouteLegRepository;
import com.haulwise.repository.ShipmentRepository;
import com.haulwise.repository.WarehouseRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Warehouse CRUD. Names are unique case-insensitively. Stored route legs of a warehouse are removed
 * when it is deleted or its coordinates change (they would be stale; estimates are used until an
 * OSRM refresh, D-06).
 */
@Service
@Transactional
public class WarehouseService {

    private final WarehouseRepository warehouses;
    private final ShipmentRepository shipments;
    private final RouteLegRepository routeLegs;

    public WarehouseService(WarehouseRepository warehouses, ShipmentRepository shipments,
                            RouteLegRepository routeLegs) {
        this.warehouses = warehouses;
        this.shipments = shipments;
        this.routeLegs = routeLegs;
    }

    @Transactional(readOnly = true)
    public List<WarehouseResponse> findAll() {
        return warehouses.findAll(Sort.by(Sort.Order.asc("name").ignoreCase())).stream().map(WarehouseResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public WarehouseResponse findById(Long id) {
        return WarehouseResponse.from(load(id));
    }

    public WarehouseResponse create(WarehouseRequest request) {
        if (warehouses.existsByNameIgnoreCase(request.name())) {
            throw nameTaken(request.name());
        }
        Warehouse warehouse = new Warehouse(request.name(), request.city(), request.lat(), request.lon());
        return WarehouseResponse.from(warehouses.save(warehouse));
    }

    public WarehouseResponse update(Long id, WarehouseRequest request) {
        Warehouse warehouse = load(id);
        if (warehouses.existsByNameIgnoreCaseAndIdNot(request.name(), id)) {
            throw nameTaken(request.name());
        }
        boolean moved = warehouse.getLat() != request.lat() || warehouse.getLon() != request.lon();
        warehouse.setName(request.name());
        warehouse.setCity(request.city());
        warehouse.setLat(request.lat());
        warehouse.setLon(request.lon());
        if (moved) {
            routeLegs.deleteByWarehouseId(id);
        }
        return WarehouseResponse.from(warehouse);
    }

    public void delete(Long id) {
        Warehouse warehouse = load(id);
        long usages = shipments.countReferencingWarehouse(id);
        if (usages > 0) {
            throw new ConflictException("Warehouse '%s' is used by %d %s.".formatted(
                    warehouse.getName(), usages, usages == 1 ? "shipment" : "shipments"));
        }
        routeLegs.deleteByWarehouseId(id);
        warehouses.delete(warehouse);
    }

    private Warehouse load(Long id) {
        return warehouses.findById(id).orElseThrow(() -> new NotFoundException("Warehouse", id));
    }

    private static ConflictException nameTaken(String name) {
        return new ConflictException("A warehouse named '" + name + "' already exists.");
    }
}
