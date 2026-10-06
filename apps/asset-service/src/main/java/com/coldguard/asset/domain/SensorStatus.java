package com.coldguard.asset.domain;

/** Operational state of a sensor; RETIRED is terminal. */
public enum SensorStatus {
  ACTIVE,
  IN_MAINTENANCE,
  INACTIVE,
  RETIRED
}
