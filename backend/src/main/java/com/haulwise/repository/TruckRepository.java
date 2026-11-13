package com.haulwise.repository;

import com.haulwise.domain.Truck;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Plates are stored normalised; pass {@link Truck#normalisePlate(String)} values to the lookups.
 */
public interface TruckRepository extends JpaRepository<Truck, Long> {

    boolean existsByPlate(String plate);

    /** Uniqueness check on update: is the plate used by any other truck? */
    boolean existsByPlateAndIdNot(String plate, Long id);

    /** Every truck with its home depot fetched, safe to map outside the session. */
    @EntityGraph(attributePaths = "homeWarehouse")
    List<Truck> findWithHomeWarehouseBy(Sort sort);

    /** One truck with its home depot fetched. */
    @EntityGraph(attributePaths = "homeWarehouse")
    Optional<Truck> findWithHomeWarehouseById(Long id);

    /** The trucks with these ids (unknown ids are skipped), home depot fetched. */
    @EntityGraph(attributePaths = "homeWarehouse")
    List<Truck> findWithHomeWarehouseByIdIn(Collection<Long> ids);

    /**
     * The candidate fleet of a depot (T-32, T-33): trucks based there and not on route, ordered by id. Bounds the
     * recommendation search by the depot's own fleet, not the whole table.
     */
    List<Truck> findByHomeWarehouseIdAndAvailableTrueOrderById(Long homeWarehouseId);

    /** Number of trucks based at the warehouse (409 on delete). */
    long countByHomeWarehouseId(Long homeWarehouseId);
}
