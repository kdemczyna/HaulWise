package com.haulwise.seed;

/**
 * One row of {@code seed/matrix.csv}: road distance and driving time between two warehouses (by name),
 * valid in both directions.
 */
public record MatrixCsvRow(String fromWarehouse, String toWarehouse, double distanceKm, double durationMinutes) {

    /** The same leg driven the other way (the seed matrix is symmetric). */
    public MatrixCsvRow reversed() {
        return new MatrixCsvRow(toWarehouse, fromWarehouse, distanceKm, durationMinutes);
    }
}
