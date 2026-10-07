package com.coldguard.simulator.infrastructure;

import com.coldguard.simulator.application.ReadingSender;
import com.coldguard.simulator.domain.PendingReading;
import com.coldguard.telemetry.grpc.v1.IngestReadingsRequest;
import com.coldguard.telemetry.grpc.v1.IngestReadingsResponse;
import com.coldguard.telemetry.grpc.v1.Reading;
import com.coldguard.telemetry.grpc.v1.ReadingResult;
import com.coldguard.telemetry.grpc.v1.ReadingSource;
import com.coldguard.telemetry.grpc.v1.TelemetryServiceGrpc;
import com.google.protobuf.Timestamp;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Delivers a batch with {@code IngestReadings(source=SIMULATOR)}. An unreachable or slow Telemetry
 * is {@code Unavailable} (the readings are kept); any other failure of the call is {@code Refused}.
 */
public class GrpcReadingSender implements ReadingSender {

  private final TelemetryServiceGrpc.TelemetryServiceBlockingStub stub;
  private final Duration deadline;

  public GrpcReadingSender(
      TelemetryServiceGrpc.TelemetryServiceBlockingStub stub, Duration deadline) {
    this.stub = stub;
    this.deadline = deadline;
  }

  @Override
  public Outcome send(List<PendingReading> batch) {
    IngestReadingsRequest.Builder request =
        IngestReadingsRequest.newBuilder().setSource(ReadingSource.READING_SOURCE_SIMULATOR);
    for (PendingReading r : batch) {
      request.addReadings(
          Reading.newBuilder()
              .setReadingId(r.readingId().toString())
              .setSensorId(r.sensorId())
              .setRecordedAt(
                  Timestamp.newBuilder()
                      .setSeconds(r.recordedAt().getEpochSecond())
                      .setNanos(r.recordedAt().getNano()))
              .setValue(r.value())
              .setUnit(r.unit()));
    }
    IngestReadingsResponse response;
    try {
      response =
          stub.withDeadlineAfter(deadline.toMillis(), TimeUnit.MILLISECONDS)
              .ingestReadings(request.build());
    } catch (StatusRuntimeException e) {
      Status.Code code = e.getStatus().getCode();
      if (code == Status.Code.UNAVAILABLE || code == Status.Code.DEADLINE_EXCEEDED) {
        return new Outcome.Unavailable(code.name());
      }
      return new Outcome.Refused(code.name());
    }
    int accepted = 0;
    int duplicates = 0;
    List<Rejection> rejections = new ArrayList<>();
    List<ReadingResult> results = response.getResultsList();
    for (int i = 0; i < results.size(); i++) {
      ReadingResult result = results.get(i);
      switch (result.getOutcome()) {
        case OUTCOME_ACCEPTED -> accepted++;
        case OUTCOME_DUPLICATE -> duplicates++;
        default ->
            rejections.add(
                new Rejection(
                    i < batch.size() ? batch.get(i).sensorId() : "unknown",
                    result.getRejectionCode()));
      }
    }
    return new Outcome.Delivered(accepted, duplicates, List.copyOf(rejections));
  }
}
