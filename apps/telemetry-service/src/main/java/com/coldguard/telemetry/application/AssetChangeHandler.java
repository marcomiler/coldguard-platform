package com.coldguard.telemetry.application;

import com.coldguard.telemetry.domain.SensorCondition;
import com.coldguard.telemetry.domain.SensorStatus;
import java.time.Instant;
import java.util.UUID;
import java.util.function.UnaryOperator;

/**
 * Applies what Asset announces about sensors to what Telemetry keeps. Every change forgets the
 * cached evaluation context first, and updates the stored condition only if the sensor has already
 * sent readings (otherwise the condition is created from Asset's answer on its first reading).
 * Changes are idempotent and ignored when an equal or newer one was already applied.
 *
 * <p>Runs inside the caller's transaction (the consumer's idempotency guard).
 */
public class AssetChangeHandler {

  private final SensorConditionRepository conditions;
  private final SensorContextEviction contexts;

  public AssetChangeHandler(SensorConditionRepository conditions, SensorContextEviction contexts) {
    this.conditions = conditions;
    this.contexts = contexts;
  }

  public void sensorStatusChanged(UUID sensorId, SensorStatus status, Instant at) {
    update(sensorId, at, condition -> condition.statusChanged(status, at));
  }

  public void sensorReassigned(UUID sensorId, UUID newAssetId, Instant at) {
    update(sensorId, at, condition -> condition.reassigned(newAssetId, at));
  }

  public void expectedIntervalChanged(UUID sensorId, int seconds, Instant at) {
    update(sensorId, at, condition -> condition.intervalChanged(seconds, at));
  }

  /** The asset's own data (criticality) is part of every evaluation context of its sensors. */
  public void assetUpdated() {
    contexts.evictAll();
  }

  private void update(UUID sensorId, Instant at, UnaryOperator<SensorCondition> change) {
    contexts.evict(sensorId);
    conditions
        .lockExisting(sensorId)
        .filter(condition -> !condition.staleChange(at))
        .ifPresent(condition -> conditions.save(change.apply(condition)));
  }
}
