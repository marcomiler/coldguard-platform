package com.coldguard.telemetry.application;

import com.coldguard.commons.messaging.EventActor;
import com.coldguard.commons.messaging.outbox.OutboundEvent;
import com.coldguard.telemetry.domain.Anomaly;
import com.coldguard.telemetry.domain.EvaluationProfile;
import com.coldguard.telemetry.domain.SensorContext;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Builds the outbound events of the Telemetry context; payloads follow contracts/events/telemetry.
 */
final class TelemetryEvents {

  private TelemetryEvents() {}

  record ThresholdBreached(
      UUID readingId,
      UUID sensorId,
      UUID assetId,
      String assetCriticality,
      String anomalyType,
      BigDecimal value,
      String unit,
      BigDecimal thresholdMin,
      BigDecimal thresholdMax,
      BigDecimal deviation,
      String magnitude,
      boolean persistent,
      Instant recordedAt) {}

  static OutboundEvent thresholdBreached(
      UUID readingId,
      SensorContext context,
      BigDecimal value,
      String unit,
      Anomaly anomaly,
      boolean persistent,
      Instant recordedAt,
      EventActor actor) {
    EvaluationProfile profile = context.profile();
    return new OutboundEvent(
        "TelemetryThresholdBreached",
        1,
        "Sensor",
        context.sensorId().toString(),
        "telemetry.threshold-breached",
        actor,
        recordedAt,
        new ThresholdBreached(
            readingId,
            context.sensorId(),
            context.assetId(),
            context.assetCriticality().name(),
            anomaly.type().name(),
            value,
            unit,
            profile.minTemperature(),
            profile.maxTemperature(),
            anomaly.deviation(),
            anomaly.magnitude().name(),
            persistent,
            recordedAt));
  }
}
