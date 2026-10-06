package com.coldguard.telemetry.api;

import com.coldguard.telemetry.application.ReadingOutcome;
import com.coldguard.telemetry.application.ReadingResult;
import com.coldguard.telemetry.application.StoredReading;
import com.coldguard.telemetry.domain.MagnitudeLevel;
import com.coldguard.telemetry.domain.ReadingSource;
import com.coldguard.telemetry.domain.SensorCondition;
import com.google.protobuf.Duration;
import com.google.protobuf.Timestamp;
import java.time.Instant;

/** Translates between the gRPC messages and the domain; no rule lives here. */
final class TelemetryGrpcMapper {

  private TelemetryGrpcMapper() {}

  static ReadingSource toDomain(com.coldguard.telemetry.grpc.v1.ReadingSource source) {
    return switch (source) {
      case READING_SOURCE_SIMULATOR -> ReadingSource.SIMULATOR;
      case READING_SOURCE_TEST_INJECTION -> ReadingSource.TEST_INJECTION;
      case READING_SOURCE_UNSPECIFIED, UNRECOGNIZED -> null;
    };
  }

  static com.coldguard.telemetry.grpc.v1.ReadingSource toGrpc(ReadingSource source) {
    return switch (source) {
      case SIMULATOR -> com.coldguard.telemetry.grpc.v1.ReadingSource.READING_SOURCE_SIMULATOR;
      case TEST_INJECTION ->
          com.coldguard.telemetry.grpc.v1.ReadingSource.READING_SOURCE_TEST_INJECTION;
    };
  }

  static com.coldguard.telemetry.grpc.v1.ReadingResult toGrpc(ReadingResult result) {
    com.coldguard.telemetry.grpc.v1.ReadingResult.Builder builder =
        com.coldguard.telemetry.grpc.v1.ReadingResult.newBuilder()
            .setReadingId(result.readingId())
            .setOutcome(toGrpc(result.outcome()))
            .setEligible(result.eligible())
            .setBreached(result.breached());
    if (result.rejectionCode() != null) {
      builder.setRejectionCode(result.rejectionCode());
    }
    return builder.build();
  }

  static com.coldguard.telemetry.grpc.v1.ReadingView toGrpc(StoredReading reading) {
    com.coldguard.telemetry.grpc.v1.ReadingView.Builder builder =
        com.coldguard.telemetry.grpc.v1.ReadingView.newBuilder()
            .setReadingId(reading.id().toString())
            .setSensorId(reading.sensorId().toString())
            .setAssetId(reading.assetId().toString())
            .setRecordedAt(timestamp(reading.recordedAt()))
            .setReceivedAt(timestamp(reading.receivedAt()))
            .setValue(reading.value().doubleValue())
            .setUnit(reading.unit())
            .setSource(toGrpc(reading.source()))
            .setEligible(reading.eligible())
            .setBreached(reading.breached());
    if (reading.ineligibilityReason() != null) {
      builder.setIneligibilityReason(reading.ineligibilityReason().name());
    }
    if (reading.anomalyType() != null) {
      builder.setAnomalyType(reading.anomalyType().name());
    }
    if (reading.magnitude() != null) {
      builder.setMagnitude(toGrpc(reading.magnitude()));
    }
    return builder.build();
  }

  private static com.coldguard.telemetry.grpc.v1.ReadingResult.Outcome toGrpc(
      ReadingOutcome outcome) {
    return switch (outcome) {
      case ACCEPTED -> com.coldguard.telemetry.grpc.v1.ReadingResult.Outcome.OUTCOME_ACCEPTED;
      case DUPLICATE -> com.coldguard.telemetry.grpc.v1.ReadingResult.Outcome.OUTCOME_DUPLICATE;
      case REJECTED -> com.coldguard.telemetry.grpc.v1.ReadingResult.Outcome.OUTCOME_REJECTED;
    };
  }

  private static com.coldguard.telemetry.grpc.v1.MagnitudeLevel toGrpc(MagnitudeLevel level) {
    return switch (level) {
      case LOW -> com.coldguard.telemetry.grpc.v1.MagnitudeLevel.MAGNITUDE_LEVEL_LOW;
      case MEDIUM -> com.coldguard.telemetry.grpc.v1.MagnitudeLevel.MAGNITUDE_LEVEL_MEDIUM;
      case HIGH -> com.coldguard.telemetry.grpc.v1.MagnitudeLevel.MAGNITUDE_LEVEL_HIGH;
      case CRITICAL -> com.coldguard.telemetry.grpc.v1.MagnitudeLevel.MAGNITUDE_LEVEL_CRITICAL;
    };
  }

  static com.coldguard.telemetry.grpc.v1.ConnectivityStatus toGrpc(SensorCondition condition) {
    var status =
        com.coldguard.telemetry.grpc.v1.ConnectivityStatus.newBuilder()
            .setSensorId(condition.sensorId().toString())
            .setAssetId(condition.assetId().toString())
            .setLastReadingAt(timestamp(condition.lastReadingAt()))
            .setExpectedReadingInterval(
                Duration.newBuilder().setSeconds(condition.expectedIntervalSeconds()));
    if (condition.connectivityLostAt() != null) {
      status.setConnectivityLostAt(timestamp(condition.connectivityLostAt()));
    }
    return status.build();
  }

  private static Timestamp timestamp(Instant instant) {
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }
}
