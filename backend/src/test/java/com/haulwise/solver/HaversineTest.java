package com.haulwise.solver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.haulwise.solver.model.GeoPoint;
import org.junit.jupiter.api.Test;

class HaversineTest {

    private static final GeoPoint WARSAW = new GeoPoint(1, 52.2297, 21.0122);
    private static final GeoPoint KRAKOW = new GeoPoint(2, 50.0647, 19.9450);
    private static final GeoPoint SZCZECIN = new GeoPoint(3, 53.4285, 14.5528);

    @Test
    void warsawToKrakowIsAbout252KmAsTheCrowFlies() {
        assertThat(Haversine.km(WARSAW, KRAKOW)).isCloseTo(252.0, within(2.0));
    }

    @Test
    void warsawToSzczecinIsAbout453KmAsTheCrowFlies() {
        assertThat(Haversine.km(WARSAW, SZCZECIN)).isCloseTo(453.0, within(3.0));
    }

    @Test
    void isSymmetricAndZeroForTheSamePoint() {
        assertThat(Haversine.km(KRAKOW, WARSAW)).isCloseTo(Haversine.km(WARSAW, KRAKOW), within(1e-9));
        assertThat(Haversine.km(WARSAW, WARSAW)).isZero();
    }

    @Test
    void quarterOfTheEquatorMatchesTheSphereRadius() {
        double km = Haversine.km(new GeoPoint(1, 0, 0), new GeoPoint(2, 0, 90));

        assertThat(km).isCloseTo(Math.PI / 2 * Haversine.EARTH_RADIUS_KM, within(1e-6));
    }
}
