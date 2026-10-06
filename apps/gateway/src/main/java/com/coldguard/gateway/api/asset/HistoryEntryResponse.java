package com.coldguard.gateway.api.asset;

import java.time.Instant;
import java.util.Map;

/** One administrative change to a sensor; automatic ones have {@code actorType} SYSTEM. */
public record HistoryEntryResponse(
    String id,
    String action,
    Map<String, Object> previousValue,
    Map<String, Object> newValue,
    String reason,
    String actorType,
    String actorId,
    Instant occurredAt) {}
