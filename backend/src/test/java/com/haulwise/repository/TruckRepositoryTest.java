package com.haulwise.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.haulwise.domain.Truck;
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

    @Test
    void persistsAndReloadsAllFieldsWithNormalisedPlate() {
        Long id = em.persistAndGetId(new Truck("  wx \t 1001a ", 33, 24000, 28.0, 10.0, 45.0), Long.class);
        em.flush();
        em.clear();

        Truck loaded = trucks.findById(id).orElseThrow();

        assertThat(loaded.getPlate()).isEqualTo("WX 1001A");
        assertThat(loaded.getCapacityPallets()).isEqualTo(33);
        assertThat(loaded.getPayloadKg()).isEqualTo(24000);
        assertThat(loaded.getBaseConsumptionLPer100Km()).isEqualTo(28.0);
        assertThat(loaded.getExtraConsumptionFullLoadLPer100Km()).isEqualTo(10.0);
        assertThat(loaded.getDriverCostPlnPerHour()).isEqualTo(45.0);
    }

    @Test
    void normalisePlateTrimsCollapsesWhitespaceAndUpperCases() {
        assertThat(Truck.normalisePlate("  po   5005e ")).isEqualTo("PO 5005E");
        assertThat(Truck.normalisePlate("wx\t1001a")).isEqualTo("WX 1001A");
        assertThat(Truck.normalisePlate(null)).isNull();
    }

    @Test
    void rejectsDuplicatePlateEvenWhenTypedInDifferentCase() {
        trucks.saveAndFlush(new Truck("WX 1001A", 33, 24000, 28.0, 10.0, 45.0));

        assertThatThrownBy(() -> trucks.saveAndFlush(new Truck("wx 1001a", 18, 12000, 22.0, 7.0, 42.0)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void plateExistenceChecks() {
        Truck first = trucks.saveAndFlush(new Truck("WX 1001A", 33, 24000, 28.0, 10.0, 45.0));
        Truck second = trucks.saveAndFlush(new Truck("WX 2002B", 33, 24000, 29.0, 10.0, 45.0));

        assertThat(trucks.existsByPlate(Truck.normalisePlate("wx 1001a"))).isTrue();
        assertThat(trucks.existsByPlate("PO 5005E")).isFalse();
        assertThat(trucks.existsByPlateAndIdNot("WX 1001A", first.getId())).isFalse();
        assertThat(trucks.existsByPlateAndIdNot("WX 1001A", second.getId())).isTrue();
    }
}
