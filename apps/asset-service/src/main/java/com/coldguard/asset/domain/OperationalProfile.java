package com.coldguard.asset.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-sensor configuration that Telemetry evaluates readings against. Asset only stores it: the
 * classification of an anomaly's magnitude lives in Telemetry. The invariants mirror the CHECK
 * constraints so a bad profile fails early with a clear message.
 */
public record OperationalProfile(
    UUID sensorId,
    BigDecimal minTemperature,
    BigDecimal maxTemperature,
    String unit,
    BigDecimal magnitudeMediumFrom,
    BigDecimal magnitudeHighFrom,
    BigDecimal magnitudeCriticalFrom,
    int persistenceMinConsecutive,
    Duration persistenceWindow,
    Duration expectedInterval,
    Duration calibrationValidity,
    Instant updatedAt,
    String updatedBy,
    long version) {

  /** NUMERIC(6,2) holds up to this magnitude. */
  private static final BigDecimal LIMIT = new BigDecimal("9999.99");

  public OperationalProfile {
    minTemperature = scaled(minTemperature, "minTemperature");
    maxTemperature = scaled(maxTemperature, "maxTemperature");
    magnitudeMediumFrom = scaled(magnitudeMediumFrom, "magnitudeBands.mediumFrom");
    magnitudeHighFrom = scaled(magnitudeHighFrom, "magnitudeBands.highFrom");
    magnitudeCriticalFrom = scaled(magnitudeCriticalFrom, "magnitudeBands.criticalFrom");
    unit = Text.required(unit, "unit", 20);
    if (minTemperature.compareTo(maxTemperature) >= 0) {
      throw new IllegalArgumentException("minTemperature must be lower than maxTemperature");
    }
    if (magnitudeMediumFrom.signum() <= 0
        || magnitudeMediumFrom.compareTo(magnitudeHighFrom) >= 0
        || magnitudeHighFrom.compareTo(magnitudeCriticalFrom) >= 0) {
      throw new IllegalArgumentException(
          "magnitude bands must satisfy 0 < mediumFrom < highFrom < criticalFrom");
    }
    if (persistenceMinConsecutive < 1) {
      throw new IllegalArgumentException("persistence.minConsecutiveBreaches must be at least 1");
    }
    if (persistenceWindow == null
        || persistenceWindow.isNegative()
        || persistenceWindow.toSeconds() < 1) {
      throw new IllegalArgumentException("persistence.window must be at least one second");
    }
    if (expectedInterval == null || expectedInterval.toSeconds() < 1) {
      throw new IllegalArgumentException("expectedReadingInterval must be at least one second");
    }
    if (calibrationValidity != null && calibrationValidity.toSeconds() < 1) {
      throw new IllegalArgumentException("calibrationValidity must be at least one second");
    }
    updatedBy = Text.required(updatedBy, "updatedBy", 100);
  }

  public OperationalProfile withVersion(long newVersion) {
    return new OperationalProfile(
        sensorId,
        minTemperature,
        maxTemperature,
        unit,
        magnitudeMediumFrom,
        magnitudeHighFrom,
        magnitudeCriticalFrom,
        persistenceMinConsecutive,
        persistenceWindow,
        expectedInterval,
        calibrationValidity,
        updatedAt,
        updatedBy,
        newVersion);
  }

  /** The profile as it travels in {@code OperationalProfileUpdated}. */
  public Map<String, Object> snapshot() {
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("minTemperature", minTemperature);
    snapshot.put("maxTemperature", maxTemperature);
    snapshot.put("unit", unit);
    snapshot.put("magnitudeMediumFrom", magnitudeMediumFrom);
    snapshot.put("magnitudeHighFrom", magnitudeHighFrom);
    snapshot.put("magnitudeCriticalFrom", magnitudeCriticalFrom);
    snapshot.put("persistenceMinConsecutive", persistenceMinConsecutive);
    snapshot.put("persistenceWindowSeconds", persistenceWindow.toSeconds());
    snapshot.put("expectedIntervalSeconds", expectedInterval.toSeconds());
    snapshot.put(
        "calibrationValiditySeconds",
        calibrationValidity == null ? null : calibrationValidity.toSeconds());
    return snapshot;
  }

  private static BigDecimal scaled(BigDecimal value, String field) {
    if (value == null) {
      throw new IllegalArgumentException(field + " is required");
    }
    BigDecimal scaled = value.setScale(2, RoundingMode.HALF_UP);
    if (scaled.abs().compareTo(LIMIT) > 0) {
      throw new IllegalArgumentException(field + " must be between -9999.99 and 9999.99");
    }
    return scaled;
  }
}
