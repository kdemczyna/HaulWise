package com.haulwise.repository;

import com.haulwise.domain.RouteLeg;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RouteLegRepository extends JpaRepository<RouteLeg, Long> {

    Optional<RouteLeg> findByFromWarehouseIdAndToWarehouseId(Long fromId, Long toId);

    /**
     * Deletes every leg starting or ending at the warehouse (before deleting it, or when its
     * coordinates change and the stored legs become stale).
     * <p>
     * Pending changes are flushed first. The persistence context is deliberately not cleared, so a
     * warehouse the caller is editing in the same transaction stays managed and its edits are kept.
     * Already loaded {@link RouteLeg} instances may be stale afterwards; do not reuse them.
     *
     * @return number of deleted legs
     */
    @Modifying(flushAutomatically = true)
    @Query("delete from RouteLeg l where l.fromWarehouse.id = :warehouseId or l.toWarehouse.id = :warehouseId")
    int deleteByWarehouseId(@Param("warehouseId") Long warehouseId);
}
