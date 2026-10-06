package com.coldguard.gateway.api.asset;

import java.time.Instant;

public record CalibrationResponse(
    String id,
    String sensorId,
    CalibrationKind kind,
    Instant performedAt,
    Instant validUntil,
    Instant recordedAt,
    String recordedBy,
    String reason) {}
