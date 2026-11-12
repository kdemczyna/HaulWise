package com.haulwise.repository;

import com.haulwise.domain.Warehouse;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {

    boolean existsByNameIgnoreCase(String name);

    /** Uniqueness check on update: is the name used by any other warehouse? */
    boolean existsByNameIgnoreCaseAndIdNot(String name, Long id);
}
