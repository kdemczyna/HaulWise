package com.haulwise.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.haulwise.domain.Priority;
import com.haulwise.domain.Shipment;
import com.haulwise.domain.Warehouse;
import java.time.LocalDateTime;
import java.util.List;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class ShipmentRepositoryTest {

    @Autowired
    private ShipmentRepository shipments;

    @Autowired
    private TestEntityManager em;

    private Warehouse waw;
    private Warehouse gdn;
    private Warehouse poz;

    @BeforeEach
    void setUp() {
        waw = em.persist(new Warehouse("WAW Central", "Warsaw", 52.2297, 21.0122));
        gdn = em.persist(new Warehouse("GDN Port", "Gdansk", 54.3520, 18.6466));
        poz = em.persist(new Warehouse("POZ West", "Poznan", 52.4064, 16.9252));
    }

    @Test
    void persistsAndReloadsAllFieldsAndRelations() {
        Shipment shipment = new Shipment("PO-1042", waw, gdn, 10, 6500);
        shipment.setPriority(Priority.HIGH);
        shipment.setDeadline(LocalDateTime.of(2026, 11, 13, 14, 30));
        shipment.setPinnedFirst(true);
        Long id = em.persistAndGetId(shipment, Long.class);
        em.flush();
        em.clear();

        Shipment loaded = shipments.findById(id).orElseThrow();

        assertThat(loaded.getReference()).isEqualTo("PO-1042");
        assertThat(loaded.getOrigin().getId()).isEqualTo(waw.getId());
        assertThat(loaded.getDestination().getId()).isEqualTo(gdn.getId());
        assertThat(loaded.getPallets()).isEqualTo(10);
        assertThat(loaded.getWeightKg()).isEqualTo(6500);
        assertThat(loaded.getPriority()).isEqualTo(Priority.HIGH);
        assertThat(loaded.getDeadline()).isEqualTo(LocalDateTime.of(2026, 11, 13, 14, 30));
        assertThat(loaded.isPinnedFirst()).isTrue();
    }

    @Test
    void defaultsToNormalPriorityNoDeadlineAndNotPinned() {
        Long id = em.persistAndGetId(new Shipment("PO-1", waw, gdn, 1, 100), Long.class);
        em.flush();
        em.clear();

        Shipment loaded = shipments.findById(id).orElseThrow();

        assertThat(loaded.getPriority()).isEqualTo(Priority.NORMAL);
        assertThat(loaded.getDeadline()).isNull();
        assertThat(loaded.isPinnedFirst()).isFalse();
    }

    @Test
    void nullPriorityFallsBackToNormal() {
        Shipment shipment = new Shipment("PO-1", waw, gdn, 1, 100);
        shipment.setPriority(null);

        assertThat(shipment.getPriority()).isEqualTo(Priority.NORMAL);
    }

    @Test
    void rejectsSameOriginAndDestinationAtDatabaseLevel() {
        assertThatThrownBy(() -> shipments.saveAndFlush(new Shipment("PO-1", waw, waw, 1, 100)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsMissingOrigin() {
        assertThatThrownBy(() -> shipments.saveAndFlush(new Shipment("PO-1", null, gdn, 1, 100)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void searchAppliesOptionalFiltersAndFetchesWarehouses() {
        Shipment urgentFromWaw = persistShipment("PO-1", waw, gdn, Priority.URGENT);
        Shipment normalFromWaw = persistShipment("PO-2", waw, poz, Priority.NORMAL);
        Shipment urgentFromPoz = persistShipment("PO-3", poz, gdn, Priority.URGENT);
        em.flush();
        em.clear();

        assertThat(ids(shipments.search(null, null)))
                .containsExactly(urgentFromWaw.getId(), normalFromWaw.getId(), urgentFromPoz.getId());
        assertThat(ids(shipments.search(Priority.URGENT, null)))
                .containsExactly(urgentFromWaw.getId(), urgentFromPoz.getId());
        assertThat(ids(shipments.search(null, waw.getId())))
                .containsExactly(urgentFromWaw.getId(), normalFromWaw.getId());
        assertThat(ids(shipments.search(Priority.URGENT, poz.getId())))
                .containsExactly(urgentFromPoz.getId());
        assertThat(shipments.search(Priority.HIGH, null)).isEmpty();

        Shipment first = shipments.search(null, null).get(0);
        assertThat(Hibernate.isInitialized(first.getOrigin())).isTrue();
        assertThat(Hibernate.isInitialized(first.getDestination())).isTrue();
    }

    @Test
    void findWithWarehousesByIdFetchesOriginAndDestination() {
        Long id = persistShipment("PO-1", waw, gdn, Priority.NORMAL).getId();
        em.flush();
        em.clear();

        Shipment loaded = shipments.findWithWarehousesById(id).orElseThrow();

        assertThat(Hibernate.isInitialized(loaded.getOrigin())).isTrue();
        assertThat(Hibernate.isInitialized(loaded.getDestination())).isTrue();
        assertThat(loaded.getDestination().getName()).isEqualTo("GDN Port");
        assertThat(shipments.findWithWarehousesById(id + 1000)).isEmpty();
    }

    @Test
    void countsShipmentsReferencingWarehouseAsOriginOrDestination() {
        persistShipment("PO-1", waw, gdn, Priority.NORMAL);
        persistShipment("PO-2", poz, gdn, Priority.NORMAL);
        persistShipment("PO-3", waw, poz, Priority.NORMAL);
        Warehouse unused = em.persist(new Warehouse("LUB East", "Lublin", 51.2465, 22.5684));
        em.flush();

        assertThat(shipments.countReferencingWarehouse(gdn.getId())).isEqualTo(2);
        assertThat(shipments.countReferencingWarehouse(waw.getId())).isEqualTo(2);
        assertThat(shipments.countReferencingWarehouse(poz.getId())).isEqualTo(2);
        assertThat(shipments.countReferencingWarehouse(unused.getId())).isZero();
    }

    private Shipment persistShipment(String reference, Warehouse origin, Warehouse destination, Priority priority) {
        Shipment shipment = new Shipment(reference, origin, destination, 2, 800);
        shipment.setPriority(priority);
        return em.persist(shipment);
    }

    private static List<Long> ids(List<Shipment> list) {
        return list.stream().map(Shipment::getId).toList();
    }
}
