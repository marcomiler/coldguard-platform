package com.coldguard.telemetry.application;

import com.coldguard.telemetry.domain.SensorCondition;
import com.coldguard.telemetry.domain.SensorContext;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SensorConditionRepository {

  /**
   * Returns the sensor's condition, creating it from {@code context} if it has none, and holds a
   * lock on it until the surrounding transaction ends, so the readings of one sensor are evaluated
   * one batch at a time.
   */
  SensorCondition lockOrCreate(SensorContext context, Instant receivedAt);

  /** Persists a condition previously returned by {@link #lockOrCreate} (still locked). */
  void save(SensorCondition condition);

  /** Locks and returns the condition of a sensor that has already sent readings, if any. */
  Optional<SensorCondition> lockExisting(UUID sensorId);

  /**
   * Locks up to {@code limit} ACTIVE sensors that have not been flagged yet and whose last reading
   * is older than their expected interval times {@code toleranceFactor}, skipping rows another
   * instance holds. Sensors with no expected interval (0) are never returned.
   */
  List<SensorCondition> lockOverdue(Instant now, double toleranceFactor, int limit);

  /**
   * One page of conditions ordered by sensor id, optionally only those flagged as not reporting.
   */
  List<SensorCondition> findPage(boolean onlyLost, int page, int size);

  long count(boolean onlyLost);
}
