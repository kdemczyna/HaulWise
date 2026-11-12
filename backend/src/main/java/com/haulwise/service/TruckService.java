package com.haulwise.service;

import com.haulwise.domain.Truck;
import com.haulwise.dto.TruckRequest;
import com.haulwise.dto.TruckResponse;
import com.haulwise.exception.ConflictException;
import com.haulwise.exception.NotFoundException;
import com.haulwise.repository.TruckRepository;
import java.util.List;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Truck CRUD. Plates arrive normalised from {@link TruckRequest} ({@link Truck#normalisePlate(String)}).
 * Trucks are freely deletable: plans are in-memory snapshots (D-05).
 */
@Service
@Transactional
public class TruckService {

    private final TruckRepository trucks;

    public TruckService(TruckRepository trucks) {
        this.trucks = trucks;
    }

    @Transactional(readOnly = true)
    public List<TruckResponse> findAll() {
        return trucks.findAll(Sort.by(Sort.Order.asc("plate").ignoreCase())).stream().map(TruckResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public TruckResponse findById(Long id) {
        return TruckResponse.from(load(id));
    }

    public TruckResponse create(TruckRequest request) {
        if (trucks.existsByPlate(request.plate())) {
            throw plateTaken(request.plate());
        }
        Truck truck = new Truck(request.plate(), request.capacityPallets(), request.payloadKg(),
                request.baseConsumptionLPer100Km(), request.extraConsumptionFullLoadLPer100Km(),
                request.driverCostPlnPerHour());
        return TruckResponse.from(trucks.save(truck));
    }

    public TruckResponse update(Long id, TruckRequest request) {
        Truck truck = load(id);
        if (trucks.existsByPlateAndIdNot(request.plate(), id)) {
            throw plateTaken(request.plate());
        }
        truck.setPlate(request.plate());
        truck.setCapacityPallets(request.capacityPallets());
        truck.setPayloadKg(request.payloadKg());
        truck.setBaseConsumptionLPer100Km(request.baseConsumptionLPer100Km());
        truck.setExtraConsumptionFullLoadLPer100Km(request.extraConsumptionFullLoadLPer100Km());
        truck.setDriverCostPlnPerHour(request.driverCostPlnPerHour());
        return TruckResponse.from(truck);
    }

    public void delete(Long id) {
        trucks.delete(load(id));
    }

    private Truck load(Long id) {
        return trucks.findById(id).orElseThrow(() -> new NotFoundException("Truck", id));
    }

    private static ConflictException plateTaken(String plate) {
        return new ConflictException("A truck with plate '" + plate + "' already exists.");
    }
}
