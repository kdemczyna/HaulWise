package com.haulwise.service;

/**
 * Settings used to estimate legs that have no stored distance (ARCHITECTURE 6.6). Internal, not an API type.
 */
public record EstimateParameters(double roadFactor, double averageSpeedKmh) {
}
