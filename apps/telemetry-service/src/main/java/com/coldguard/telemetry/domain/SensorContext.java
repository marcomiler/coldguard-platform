package com.coldguard.telemetry.domain;

import java.util.UUID;

/**
 * What Asset says about a sensor at the moment a reading arrives. {@code profile} is null when the
 * sensor has none, which makes its readings not evaluable.
 */
public record SensorContext(
    UUID sensorId,
    UUID assetId,
    Criticality assetCriticality,
    SensorStatus status,
    EvaluationProfile profile) {

  /** Why a reading of this sensor would not be evaluated, or null when it would. */
  public IneligibilityReason ineligibility() {
    if (status != SensorStatus.ACTIVE) {
      return IneligibilityReason.SENSOR_NOT_ACTIVE;
    }
    return profile == null ? IneligibilityReason.NO_PROFILE : null;
  }
}
