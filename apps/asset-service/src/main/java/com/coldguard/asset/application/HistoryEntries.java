package com.coldguard.asset.application;

import com.coldguard.commons.security.Actor;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Builds the history entry of an administrative change made by {@code actor}. */
final class HistoryEntries {

  private HistoryEntries() {}

  static SensorHistoryEntry of(
      Actor actor,
      UUID sensorId,
      String action,
      Map<String, Object> previousValue,
      Map<String, Object> newValue,
      String reason,
      Instant now) {
    return new SensorHistoryEntry(
        UUID.randomUUID(),
        sensorId,
        action,
        previousValue,
        newValue,
        reason,
        Actors.type(actor),
        actor.id(),
        now);
  }
}
