package com.haulwise.solver;

import com.haulwise.solver.model.GeoPoint;

/**
 * Great-circle distance on a spherical Earth (mean radius 6371 km).
 */
public final class Haversine {

    static final double EARTH_RADIUS_KM = 6371.0;

    private Haversine() {
    }

    public static double km(GeoPoint a, GeoPoint b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLon = Math.toRadians(b.lon() - a.lon());
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat()))
                * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1, Math.sqrt(h)));
    }
}
