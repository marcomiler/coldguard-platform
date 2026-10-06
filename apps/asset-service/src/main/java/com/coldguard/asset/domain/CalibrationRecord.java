package com.coldguard.asset.domain;

import java.time.Instant;
import java.util.UUID;

/** Evidence that a sensor was calibrated or verified; append-only. */
public record CalibrationRecord(
    UUID id,
    UUID sensorId,
    CalibrationKind kind,
    Instant performedAt,
    Instant validUntil,
    Instant recordedAt,
    String recordedBy,
    String reason) {

  public CalibrationRecord {
    if (kind == null) {
      throw new IllegalArgumentException("calibration kind is required");
    }
    if (performedAt == null) {
      throw new IllegalArgumentException("performedAt is required");
    }
    reason = Text.required(reason, "reason", 500);
    recordedBy = Text.required(recordedBy, "recordedBy", 100);
  }
}
