package com.haulwise.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.haulwise.domain.LegSource;
import com.haulwise.domain.RouteLeg;
import com.haulwise.domain.Warehouse;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

@DataJpaTest
class RouteLegRepositoryTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 8, 0);

    @Autowired
    private RouteLegRepository legs;

    @Autowired
    private TestEntityManager em;

    private Warehouse waw;
    private Warehouse krk;
    private Warehouse ldz;

    @BeforeEach
    void setUp() {
        waw = em.persist(new Warehouse("WAW Central", "Warsaw", 52.2297, 21.0122));
        krk = em.persist(new Warehouse("KRK South", "Krakow", 50.0647, 19.9450));
        ldz = em.persist(new Warehouse("LDZ Cross-dock", "Lodz", 51.7592, 19.4560));
    }

    @Test
    void storesDirectedLegsAndFindsThemByPair() {
        legs.save(new RouteLeg(waw, krk, 295, 253, LegSource.SEEDED, NOW));
        legs.save(new RouteLeg(krk, waw, 296, 255, LegSource.OSRM, NOW));
        em.flush();
        em.clear();

        RouteLeg there = legs.findByFromWarehouseIdAndToWarehouseId(waw.getId(), krk.getId()).orElseThrow();
        RouteLeg back = legs.findByFromWarehouseIdAndToWarehouseId(krk.getId(), waw.getId()).orElseThrow();

        assertThat(there.getDistanceKm()).isEqualTo(295);
        assertThat(there.getDurationMinutes()).isEqualTo(253);
        assertThat(there.getSource()).isEqualTo(LegSource.SEEDED);
        assertThat(there.getUpdatedAt()).isEqualTo(NOW);
        assertThat(back.getSource()).isEqualTo(LegSource.OSRM);
        assertThat(legs.findByFromWarehouseIdAndToWarehouseId(waw.getId(), ldz.getId())).isEmpty();
    }

    @Test
    void findLegsBetweenReturnsIdProjectionsOfLegsInsideTheSet() {
        legs.save(new RouteLeg(waw, krk, 295, 253, LegSource.SEEDED, NOW));
        legs.save(new RouteLeg(krk, waw, 296.5, 255, LegSource.OSRM, NOW));
        legs.save(new RouteLeg(krk, ldz, 275, 236, LegSource.SEEDED, NOW));
        em.flush();
        em.clear();

        List<StoredLeg> found = legs.findLegsBetween(List.of(waw.getId(), krk.getId()));

        assertThat(found).containsExactlyInAnyOrder(
                new StoredLeg(waw.getId(), krk.getId(), 295, 253, LegSource.SEEDED),
                new StoredLeg(krk.getId(), waw.getId(), 296.5, 255, LegSource.OSRM));
        assertThat(legs.findLegsBetween(List.of(ldz.getId()))).isEmpty();
    }

    @Test
    void rejectsSecondLegForTheSameDirectedPair() {
        legs.saveAndFlush(new RouteLeg(waw, krk, 295, 253, LegSource.SEEDED, NOW));

        assertThatThrownBy(() -> legs.saveAndFlush(new RouteLeg(waw, krk, 300, 260, LegSource.OSRM, NOW)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void updateOverwritesMeasuredValues() {
        RouteLeg leg = legs.saveAndFlush(new RouteLeg(waw, krk, 295, 253, LegSource.SEEDED, NOW));

        leg.update(301.5, 262.0, LegSource.OSRM, NOW.plusDays(1));
        em.flush();
        em.clear();

        RouteLeg loaded = legs.findById(leg.getId()).orElseThrow();
        assertThat(loaded.getDistanceKm()).isEqualTo(301.5);
        assertThat(loaded.getSource()).isEqualTo(LegSource.OSRM);
        assertThat(loaded.getUpdatedAt()).isEqualTo(NOW.plusDays(1));
    }

    @Test
    void deleteByWarehouseIdRemovesLegsInBothDirectionsOnly() {
        legs.save(new RouteLeg(waw, krk, 295, 253, LegSource.SEEDED, NOW));
        legs.save(new RouteLeg(krk, waw, 295, 253, LegSource.SEEDED, NOW));
        RouteLeg unrelated = legs.save(new RouteLeg(krk, ldz, 275, 236, LegSource.SEEDED, NOW));

        int deleted = legs.deleteByWarehouseId(waw.getId());

        assertThat(deleted).isEqualTo(2);
        assertThat(legs.findAll()).extracting(RouteLeg::getId).containsExactly(unrelated.getId());
    }

    @Test
    void deleteByWarehouseIdKeepsPendingEditsOfTheWarehouse() {
        legs.saveAndFlush(new RouteLeg(waw, krk, 295, 253, LegSource.SEEDED, NOW));

        waw.setLat(52.25);
        legs.deleteByWarehouseId(waw.getId());
        em.flush();
        em.clear();

        assertThat(em.find(Warehouse.class, waw.getId()).getLat()).isEqualTo(52.25);
        assertThat(legs.count()).isZero();
    }
}
