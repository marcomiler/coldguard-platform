package com.coldguard.asset.application;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** One administrative change to a sensor: who, when, why, and the value before and after. */
public record SensorHistoryEntry(
    UUID id,
    UUID sensorId,
    String action,
    Map<String, Object> previousValue,
    Map<String, Object> newValue,
    String reason,
    String actorType,
    String actorId,
    Instant occurredAt) {}
