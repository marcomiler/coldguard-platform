package com.coldguard.telemetry.domain;

/** Why a reading is kept as evidence but not evaluated. */
public enum IneligibilityReason {
  SENSOR_NOT_ACTIVE,
  NO_PROFILE
}
