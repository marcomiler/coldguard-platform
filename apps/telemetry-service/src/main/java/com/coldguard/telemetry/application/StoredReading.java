package com.coldguard.telemetry.application;

import com.coldguard.telemetry.domain.AnomalyType;
import com.coldguard.telemetry.domain.IneligibilityReason;
import com.coldguard.telemetry.domain.MagnitudeLevel;
import com.coldguard.telemetry.domain.ReadingSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A persisted reading: evidence, with what the evaluation concluded about it. */
public record StoredReading(
    UUID id,
    UUID sensorId,
    UUID assetId,
    Instant recordedAt,
    Instant receivedAt,
    BigDecimal value,
    String unit,
    ReadingSource source,
    boolean eligible,
    IneligibilityReason ineligibilityReason,
    boolean breached,
    AnomalyType anomalyType,
    MagnitudeLevel magnitude,
    String correlationId) {}
