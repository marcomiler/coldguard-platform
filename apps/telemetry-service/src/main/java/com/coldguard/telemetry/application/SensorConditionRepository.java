package com.coldguard.telemetry.application;

import com.coldguard.telemetry.domain.SensorCondition;
import com.coldguard.telemetry.domain.SensorContext;
import java.time.Instant;

public interface SensorConditionRepository {

  /**
   * Returns the sensor's condition, creating it from {@code context} if it has none, and holds a
   * lock on it until the surrounding transaction ends, so the readings of one sensor are evaluated
   * one batch at a time.
   */
  SensorCondition lockOrCreate(SensorContext context, Instant receivedAt);

  /** Persists a condition previously returned by {@link #lockOrCreate} (still locked). */
  void save(SensorCondition condition);
}
