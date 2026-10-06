package com.coldguard.gateway.api.telemetry;

import com.coldguard.telemetry.grpc.v1.IngestReadingsRequest;
import com.coldguard.telemetry.grpc.v1.ListReadingsRequest;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.UUID;

/**
 * REST ↔ gRPC translation for telemetry. The two vocabularies stay independent: the REST enums have
 * no protocol prefix and no REST type exposes a generated class. It decides nothing.
 */
final class TelemetryRestMapper {

  private static final String SOURCE = "READING_SOURCE_";
  private static final String OUTCOME = "OUTCOME_";
  private static final String MAGNITUDE = "MAGNITUDE_LEVEL_";

  private TelemetryRestMapper() {}

  /** Test injections always carry that source; the client cannot choose it. */
  static IngestReadingsRequest injection(TestReadingsRequest request, Instant now) {
    IngestReadingsRequest.Builder builder =
        IngestReadingsRequest.newBuilder()
            .setSource(com.coldguard.telemetry.grpc.v1.ReadingSource.READING_SOURCE_TEST_INJECTION);
    for (TestReading reading : request.readings()) {
      builder.addReadings(
          com.coldguard.telemetry.grpc.v1.Reading.newBuilder()
              .setReadingId(
                  reading.readingId() == null || reading.readingId().isBlank()
                      ? UUID.randomUUID().toString()
                      : reading.readingId())
              .setSensorId(reading.sensorId())
              .setRecordedAt(timestamp(reading.recordedAt() == null ? now : reading.recordedAt()))
              .setValue(reading.value())
              .setUnit(reading.unit()));
    }
    return builder.build();
  }

  static ListReadingsRequest list(
      String sensorId, Instant from, Instant to, String cursor, int size) {
    return ListReadingsRequest.newBuilder()
        .setSensorId(sensorId)
        .setFrom(timestamp(from))
        .setTo(timestamp(to))
        .setPage(
            com.coldguard.common.grpc.v1.CursorPageRequest.newBuilder()
                .setCursor(cursor == null ? "" : cursor)
                .setSize(size))
        .build();
  }

  static ReadingResult toRest(com.coldguard.telemetry.grpc.v1.ReadingResult result) {
    return new ReadingResult(
        result.getReadingId(),
        ReadingOutcome.valueOf(strip(result.getOutcome().name(), OUTCOME)),
        result.getEligible(),
        result.getBreached(),
        result.getRejectionCode().isEmpty() ? null : result.getRejectionCode());
  }

  static ReadingResponse toRest(com.coldguard.telemetry.grpc.v1.ReadingView reading) {
    return new ReadingResponse(
        reading.getReadingId(),
        reading.getSensorId(),
        reading.getAssetId(),
        instant(reading.getRecordedAt()),
        instant(reading.getReceivedAt()),
        reading.getValue(),
        reading.getUnit(),
        ReadingSource.valueOf(strip(reading.getSource().name(), SOURCE)),
        reading.getEligible(),
        reading.getIneligibilityReason().isEmpty() ? null : reading.getIneligibilityReason(),
        reading.getBreached(),
        reading.getAnomalyType().isEmpty() ? null : reading.getAnomalyType(),
        reading.getMagnitude()
                == com.coldguard.telemetry.grpc.v1.MagnitudeLevel.MAGNITUDE_LEVEL_UNSPECIFIED
            ? null
            : MagnitudeLevel.valueOf(strip(reading.getMagnitude().name(), MAGNITUDE)));
  }

  private static Timestamp timestamp(Instant instant) {
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }

  private static Instant instant(Timestamp timestamp) {
    return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
  }

  private static String strip(String name, String prefix) {
    return name.substring(prefix.length());
  }
}
