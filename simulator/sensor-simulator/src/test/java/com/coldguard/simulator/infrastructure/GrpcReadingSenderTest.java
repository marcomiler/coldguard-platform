package com.coldguard.simulator.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.coldguard.simulator.application.ReadingSender.Outcome;
import com.coldguard.simulator.domain.PendingReading;
import com.coldguard.telemetry.grpc.v1.IngestReadingsRequest;
import com.coldguard.telemetry.grpc.v1.IngestReadingsResponse;
import com.coldguard.telemetry.grpc.v1.ReadingResult;
import com.coldguard.telemetry.grpc.v1.ReadingSource;
import com.coldguard.telemetry.grpc.v1.TelemetryServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The adapter against a scriptable in-process Telemetry. */
class GrpcReadingSenderTest {

  private Server server;
  private ManagedChannel channel;

  @AfterEach
  void tearDown() throws InterruptedException {
    channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
  }

  private GrpcReadingSender senderAnswering(
      AtomicReference<IngestReadingsRequest> captured,
      Consumer<StreamObserver<IngestReadingsResponse>> behaviour,
      Duration deadline)
      throws Exception {
    String name = "sim-" + UUID.randomUUID();
    server =
        InProcessServerBuilder.forName(name)
            .addService(
                new TelemetryServiceGrpc.TelemetryServiceImplBase() {
                  @Override
                  public void ingestReadings(
                      IngestReadingsRequest request,
                      StreamObserver<IngestReadingsResponse> observer) {
                    captured.set(request);
                    behaviour.accept(observer);
                  }
                })
            .build()
            .start();
    channel = InProcessChannelBuilder.forName(name).build();
    return new GrpcReadingSender(TelemetryServiceGrpc.newBlockingStub(channel), deadline);
  }

  private static List<PendingReading> batch() {
    return List.of(
        new PendingReading(
            UUID.randomUUID(), "sensor-a", Instant.parse("2026-10-01T10:00:00Z"), 4.25, "CELSIUS"),
        new PendingReading(
            UUID.randomUUID(), "sensor-b", Instant.parse("2026-10-01T10:00:01Z"), 5.0, "CELSIUS"));
  }

  @Test
  void sendsTheBatchAsSimulatorReadingsKeepingTheirIds() throws Exception {
    var captured = new AtomicReference<IngestReadingsRequest>();
    GrpcReadingSender sender =
        senderAnswering(
            captured,
            observer -> {
              observer.onNext(IngestReadingsResponse.getDefaultInstance());
              observer.onCompleted();
            },
            Duration.ofSeconds(2));
    List<PendingReading> batch = batch();

    sender.send(batch);

    assertThat(captured.get().getSource()).isEqualTo(ReadingSource.READING_SOURCE_SIMULATOR);
    assertThat(captured.get().getReadingsList()).hasSize(2);
    assertThat(captured.get().getReadings(0).getReadingId())
        .isEqualTo(batch.get(0).readingId().toString());
    assertThat(captured.get().getReadings(0).getValue()).isEqualTo(4.25);
    assertThat(captured.get().getReadings(0).getRecordedAt().getSeconds())
        .isEqualTo(batch.get(0).recordedAt().getEpochSecond());
    assertThat(captured.get().getReadings(1).getSensorId()).isEqualTo("sensor-b");
  }

  @Test
  void countsAcceptedDuplicatesAndRejectionsBySensorAndCode() throws Exception {
    GrpcReadingSender sender =
        senderAnswering(
            new AtomicReference<>(),
            observer -> {
              observer.onNext(
                  IngestReadingsResponse.newBuilder()
                      .addResults(
                          ReadingResult.newBuilder()
                              .setOutcome(ReadingResult.Outcome.OUTCOME_DUPLICATE))
                      .addResults(
                          ReadingResult.newBuilder()
                              .setOutcome(ReadingResult.Outcome.OUTCOME_REJECTED)
                              .setRejectionCode("UNIT_MISMATCH"))
                      .build());
              observer.onCompleted();
            },
            Duration.ofSeconds(2));

    Outcome outcome = sender.send(batch());

    assertThat(outcome)
        .isInstanceOfSatisfying(
            Outcome.Delivered.class,
            delivered -> {
              assertThat(delivered.duplicates()).isEqualTo(1);
              assertThat(delivered.accepted()).isZero();
              assertThat(delivered.rejections()).hasSize(1);
              assertThat(delivered.rejections().get(0).sensorId()).isEqualTo("sensor-b");
              assertThat(delivered.rejections().get(0).code()).isEqualTo("UNIT_MISMATCH");
            });
  }

  @Test
  void anUnavailableTelemetryIsRetryableAndAnythingElseIsRefused() throws Exception {
    assertThat(
            senderAnswering(
                    new AtomicReference<>(),
                    observer -> observer.onError(Status.UNAVAILABLE.asRuntimeException()),
                    Duration.ofSeconds(2))
                .send(batch()))
        .isInstanceOf(Outcome.Unavailable.class);
    tearDown();

    assertThat(
            senderAnswering(
                    new AtomicReference<>(),
                    observer -> observer.onError(Status.PERMISSION_DENIED.asRuntimeException()),
                    Duration.ofSeconds(2))
                .send(batch()))
        .isInstanceOf(Outcome.Refused.class);
  }

  @Test
  void aSlowTelemetryIsRetryableAfterTheDeadline() throws Exception {
    GrpcReadingSender sender =
        senderAnswering(new AtomicReference<>(), observer -> {}, Duration.ofMillis(200));

    assertThat(sender.send(batch())).isInstanceOf(Outcome.Unavailable.class);
  }
}
