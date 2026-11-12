package com.haulwise.solver.model;

/**
 * A warehouse position, used for distance estimates.
 */
public record GeoPoint(long warehouseId, double lat, double lon) {

    public GeoPoint {
        if (!Double.isFinite(lat) || lat < -90 || lat > 90) {
            throw new IllegalArgumentException("lat must be within -90..90, was " + lat);
        }
        if (!Double.isFinite(lon) || lon < -180 || lon > 180) {
            throw new IllegalArgumentException("lon must be within -180..180, was " + lon);
        }
    }
}
