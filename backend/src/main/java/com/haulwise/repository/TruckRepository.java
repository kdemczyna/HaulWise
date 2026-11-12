package com.haulwise.repository;

import com.haulwise.domain.Truck;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Plates are stored normalised; pass {@link Truck#normalisePlate(String)} values to the lookups.
 */
public interface TruckRepository extends JpaRepository<Truck, Long> {

    boolean existsByPlate(String plate);

    /** Uniqueness check on update: is the plate used by any other truck? */
    boolean existsByPlateAndIdNot(String plate, Long id);
}
