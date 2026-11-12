package com.haulwise.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST/PUT /api/warehouses}. Text fields have every run of whitespace (Unicode-aware, so
 * no-break spaces too) collapsed to one space and are stripped on construction, so validation, the uniqueness check and storage see the cleaned value.
 */
public record WarehouseRequest(
        @NotNull @Size(min = 2, max = 60) String name,
        @NotNull @Size(min = 2, max = 60) String city,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lon) {

    public WarehouseRequest {
        name = clean(name);
        city = clean(city);
    }

    private static String clean(String text) {
        return text == null ? null : text.replaceAll("(?U)\\s+", " ").strip();
    }
}
