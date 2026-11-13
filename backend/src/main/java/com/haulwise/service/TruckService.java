package com.haulwise.service;

import com.haulwise.domain.Truck;
import com.haulwise.domain.Warehouse;
import com.haulwise.dto.TruckRequest;
import com.haulwise.dto.TruckResponse;
import com.haulwise.exception.ConflictException;
import com.haulwise.exception.NotFoundException;
import com.haulwise.exception.RequestValidationException;
import com.haulwise.repository.TruckRepository;
import com.haulwise.repository.WarehouseRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Truck CRUD. Plates arrive normalised from {@link TruckRequest} ({@link Truck#normalisePlate(String)}); the home
 * depot must exist (400 on {@code homeWarehouseId}). Trucks are freely deletable: plans are in-memory snapshots
 * (D-05). {@code available} is toggled through the normal full update (T-33).
 */
@Service
@Transactional
public class TruckService {

    private final TruckRepository trucks;
    private final WarehouseRepository warehouses;

    public TruckService(TruckRepository trucks, WarehouseRepository warehouses) {
        this.trucks = trucks;
        this.warehouses = warehouses;
    }

    @Transactional(readOnly = true)
    public List<TruckResponse> findAll() {
        return trucks.findWithHomeWarehouseBy(Sort.by(Sort.Order.asc("plate").ignoreCase())).stream()
                .map(TruckResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TruckResponse findById(Long id) {
        return TruckResponse.from(load(id));
    }

    public TruckResponse create(TruckRequest request) {
        Warehouse home = homeWarehouse(request.homeWarehouseId());
        if (trucks.existsByPlate(request.plate())) {
            throw plateTaken(request.plate());
        }
        Truck truck = new Truck(request.plate(), request.capacityPallets(), request.payloadKg(),
                request.baseConsumptionLPer100Km(), request.extraConsumptionFullLoadLPer100Km(),
                request.driverCostPlnPerHour(), home);
        truck.setAvailable(!Boolean.FALSE.equals(request.available()));
        return TruckResponse.from(trucks.save(truck));
    }

    public TruckResponse update(Long id, TruckRequest request) {
        Truck truck = load(id);
        Warehouse home = homeWarehouse(request.homeWarehouseId());
        if (trucks.existsByPlateAndIdNot(request.plate(), id)) {
            throw plateTaken(request.plate());
        }
        truck.setPlate(request.plate());
        truck.setCapacityPallets(request.capacityPallets());
        truck.setPayloadKg(request.payloadKg());
        truck.setBaseConsumptionLPer100Km(request.baseConsumptionLPer100Km());
        truck.setExtraConsumptionFullLoadLPer100Km(request.extraConsumptionFullLoadLPer100Km());
        truck.setDriverCostPlnPerHour(request.driverCostPlnPerHour());
        truck.setHomeWarehouse(home);
        if (request.available() != null) {
            truck.setAvailable(request.available());
        }
        return TruckResponse.from(truck);
    }

    public void delete(Long id) {
        trucks.delete(load(id));
    }

    private Truck load(Long id) {
        return trucks.findWithHomeWarehouseById(id).orElseThrow(() -> new NotFoundException("Truck", id));
    }

    /** The referenced home depot; {@code null} is already rejected by {@code @NotNull}. */
    private Warehouse homeWarehouse(Long id) {
        return warehouses.findById(id).orElseThrow(() ->
                new RequestValidationException("homeWarehouseId", "warehouse " + id + " does not exist"));
    }

    private static ConflictException plateTaken(String plate) {
        return new ConflictException("A truck with plate '" + plate + "' already exists.");
    }
}
