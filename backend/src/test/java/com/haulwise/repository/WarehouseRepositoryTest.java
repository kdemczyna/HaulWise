package com.haulwise.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.haulwise.domain.Warehouse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class WarehouseRepositoryTest {

    @Autowired
    private WarehouseRepository warehouses;

    @Autowired
    private TestEntityManager em;

    @Test
    void persistsAndReloadsAllFields() {
        Long id = em.persistAndGetId(new Warehouse("WAW Central", "Warsaw", 52.2297, 21.0122), Long.class);
        em.flush();
        em.clear();

        Warehouse loaded = warehouses.findById(id).orElseThrow();

        assertThat(loaded.getName()).isEqualTo("WAW Central");
        assertThat(loaded.getCity()).isEqualTo("Warsaw");
        assertThat(loaded.getLat()).isEqualTo(52.2297);
        assertThat(loaded.getLon()).isEqualTo(21.0122);
    }

    @Test
    void rejectsDuplicateNameAtDatabaseLevel() {
        warehouses.saveAndFlush(new Warehouse("WAW Central", "Warsaw", 52.2, 21.0));

        assertThatThrownBy(() -> warehouses.saveAndFlush(new Warehouse("WAW Central", "Radom", 51.4, 21.1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void nameExistenceChecksIgnoreCase() {
        Warehouse waw = warehouses.saveAndFlush(new Warehouse("WAW Central", "Warsaw", 52.2, 21.0));
        Warehouse krk = warehouses.saveAndFlush(new Warehouse("KRK South", "Krakow", 50.1, 19.9));

        assertThat(warehouses.existsByNameIgnoreCase("waw central")).isTrue();
        assertThat(warehouses.existsByNameIgnoreCase("GDN Port")).isFalse();
        assertThat(warehouses.existsByNameIgnoreCaseAndIdNot("WAW CENTRAL", waw.getId())).isFalse();
        assertThat(warehouses.existsByNameIgnoreCaseAndIdNot("waw central", krk.getId())).isTrue();
    }
}
