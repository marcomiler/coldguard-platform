package com.coldguard.telemetry.domain;

/** The Asset sensor status as Telemetry needs it; only ACTIVE sensors are evaluated. */
public enum SensorStatus {
  ACTIVE,
  IN_MAINTENANCE,
  INACTIVE,
  RETIRED
}
