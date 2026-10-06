package com.coldguard.asset.application;

import java.time.Instant;
import java.util.UUID;

/** A sensor's attachment to an asset; {@code previousAssetId} is null for the first one. */
public record AssignmentEntry(
    UUID id,
    UUID sensorId,
    UUID assetId,
    UUID previousAssetId,
    Instant assignedAt,
    String assignedBy,
    String reason) {}
