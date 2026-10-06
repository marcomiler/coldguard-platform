package com.coldguard.gateway.api.telemetry;

import java.time.Instant;

public record ReadingResponse(
    String readingId,
    String sensorId,
    String assetId,
    Instant recordedAt,
    Instant receivedAt,
    double value,
    String unit,
    ReadingSource source,
    boolean eligible,
    String ineligibilityReason,
    boolean breached,
    String anomalyType,
    MagnitudeLevel magnitude) {}
