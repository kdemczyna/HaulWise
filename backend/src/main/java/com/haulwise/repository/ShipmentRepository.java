package com.haulwise.repository;

import com.haulwise.domain.Priority;
import com.haulwise.domain.Shipment;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    /** One shipment with origin and destination fetched, safe to map outside the session. */
    @EntityGraph(attributePaths = {"origin", "destination"})
    Optional<Shipment> findWithWarehousesById(Long id);

    /**
     * Shipments matching the optional filters ({@code null} = no filter), with origin and destination
     * fetched so they can be mapped outside the session. Ordered by id.
     */
    @Query("""
            select s from Shipment s
              join fetch s.origin
              join fetch s.destination
            where (:priority is null or s.priority = :priority)
              and (:originId is null or s.origin.id = :originId)
            order by s.id
            """)
    List<Shipment> search(@Param("priority") Priority priority, @Param("originId") Long originId);

    /** Number of shipments that use the warehouse as origin or destination (409 on delete). */
    @Query("select count(s) from Shipment s where s.origin.id = :warehouseId or s.destination.id = :warehouseId")
    long countReferencingWarehouse(@Param("warehouseId") Long warehouseId);
}
