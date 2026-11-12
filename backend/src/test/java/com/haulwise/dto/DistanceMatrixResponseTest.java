package com.haulwise.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.haulwise.domain.Warehouse;
import com.haulwise.solver.model.DirectedPair;
import com.haulwise.solver.model.DistanceMatrix;
import com.haulwise.solver.model.DistanceSource;
import com.haulwise.solver.model.LegDistance;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class DistanceMatrixResponseTest {

    private static final DistanceMatrix MATRIX = new DistanceMatrix(List.of(1L, 2L), Map.of(
            new DirectedPair(1, 2), new LegDistance(295.04, 253.5, DistanceSource.SEEDED),
            new DirectedPair(2, 1), new LegDistance(295.06, 253.4, DistanceSource.ESTIMATED)));

    @Test
    void mapsWarehousesAndRoundsLegs() {
        DistanceMatrixResponse response = DistanceMatrixResponse.from(
                List.of(warehouse(1, "WAW Central"), warehouse(2, "KRK South")), MATRIX);

        assertThat(response.warehouses()).containsExactly(
                new DistanceMatrixResponse.WarehouseRef(1L, "WAW Central"),
                new DistanceMatrixResponse.WarehouseRef(2L, "KRK South"));
        assertThat(response.legs()).containsExactly(
                new DistanceMatrixResponse.Leg(1L, 2L, 295.0, 254, DistanceSource.SEEDED),
                new DistanceMatrixResponse.Leg(2L, 1L, 295.1, 253, DistanceSource.ESTIMATED));
    }

    @Test
    void warehousesInADifferentOrderThanTheMatrixAreRejected() {
        List<Warehouse> swapped = List.of(warehouse(2, "KRK South"), warehouse(1, "WAW Central"));

        assertThatThrownBy(() -> DistanceMatrixResponse.from(swapped, MATRIX))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("do not match");
    }

    private static Warehouse warehouse(long id, String name) {
        Warehouse warehouse = new Warehouse(name, "City", 52.0, 20.0);
        ReflectionTestUtils.setField(warehouse, "id", id);
        return warehouse;
    }
}
