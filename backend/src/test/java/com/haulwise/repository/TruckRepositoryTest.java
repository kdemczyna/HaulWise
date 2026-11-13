package com.haulwise.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.haulwise.domain.Truck;
import com.haulwise.domain.Warehouse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class TruckRepositoryTest {

    @Autowired
    private TruckRepository trucks;

    @Autowired
    private TestEntityManager em;

    private Warehouse home;

    @BeforeEach
    void createHomeDepot() {
        home = em.persist(new Warehouse("WAW Central", "Warsaw", 52.2297, 21.0122));
    }

    @Test
    void persistsAndReloadsAllFieldsWithNormalisedPlate() {
        Long id = em.persistAndGetId(truck("  wx \t 1001a ", home), Long.class);
        em.flush();
        em.clear();

        Truck loaded = trucks.findWithHomeWarehouseById(id).orElseThrow();

        assertThat(loaded.getPlate()).isEqualTo("WX 1001A");
        assertThat(loaded.getCapacityPallets()).isEqualTo(33);
        assertThat(loaded.getPayloadKg()).isEqualTo(24000);
        assertThat(loaded.getBaseConsumptionLPer100Km()).isEqualTo(28.0);
        assertThat(loaded.getExtraConsumptionFullLoadLPer100Km()).isEqualTo(10.0);
        assertThat(loaded.getDriverCostPlnPerHour()).isEqualTo(45.0);
        assertThat(loaded.getHomeWarehouse().getName()).isEqualTo("WAW Central");
        assertThat(loaded.isAvailable()).isTrue();
    }

    @Test
    void normalisePlateTrimsCollapsesWhitespaceAndUpperCases() {
        assertThat(Truck.normalisePlate("  po   5005e ")).isEqualTo("PO 5005E");
        assertThat(Truck.normalisePlate("wx\t1001a")).isEqualTo("WX 1001A");
        assertThat(Truck.normalisePlate(null)).isNull();
    }

    @Test
    void rejectsDuplicatePlateEvenWhenTypedInDifferentCase() {
        trucks.saveAndFlush(truck("WX 1001A", home));

        assertThatThrownBy(() -> trucks.saveAndFlush(truck("wx 1001a", home)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsTruckWithoutHomeDepot() {
        assertThatThrownBy(() -> trucks.saveAndFlush(truck("WX 1001A", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void plateExistenceChecks() {
        Truck first = trucks.saveAndFlush(truck("WX 1001A", home));
        Truck second = trucks.saveAndFlush(truck("WX 2002B", home));

        assertThat(trucks.existsByPlate(Truck.normalisePlate("wx 1001a"))).isTrue();
        assertThat(trucks.existsByPlate("PO 5005E")).isFalse();
        assertThat(trucks.existsByPlateAndIdNot("WX 1001A", first.getId())).isFalse();
        assertThat(trucks.existsByPlateAndIdNot("WX 1001A", second.getId())).isTrue();
    }

    @Test
    void depotFleetHasOnlyAvailableTrucksBasedThereOrderedById() {
        Warehouse other = em.persist(new Warehouse("POZ West", "Poznan", 52.4064, 16.9252));
        Truck first = trucks.save(truck("WX 1001A", home));
        Truck onRoute = truck("WX 2002B", home);
        onRoute.setAvailable(false);
        trucks.save(onRoute);
        trucks.save(truck("PO 6006F", other));
        Truck last = trucks.saveAndFlush(truck("WX 3003C", home));

        assertThat(trucks.findByHomeWarehouseIdAndAvailableTrueOrderById(home.getId()))
                .extracting(Truck::getPlate)
                .containsExactly(first.getPlate(), last.getPlate());
        assertThat(trucks.countByHomeWarehouseId(home.getId())).isEqualTo(3);
        assertThat(trucks.countByHomeWarehouseId(other.getId())).isEqualTo(1);
    }

    private static Truck truck(String plate, Warehouse homeWarehouse) {
        return new Truck(plate, 33, 24000, 28.0, 10.0, 45.0, homeWarehouse);
    }
}
