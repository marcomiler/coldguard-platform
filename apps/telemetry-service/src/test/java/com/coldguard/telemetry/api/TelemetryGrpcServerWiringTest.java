package com.coldguard.telemetry.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.ActorServerInterceptor;
import com.coldguard.commons.security.Role;
import com.coldguard.telemetry.application.AssetUnavailableException;
import com.coldguard.telemetry.application.IngestReadingsService;
import com.coldguard.telemetry.application.IngestSettings;
import com.coldguard.telemetry.application.ReadingQueryService;
import com.coldguard.telemetry.domain.Criticality;
import com.coldguard.telemetry.domain.EvaluationProfile;
import com.coldguard.telemetry.domain.SensorContext;
import com.coldguard.telemetry.domain.SensorStatus;
import com.coldguard.telemetry.grpc.v1.IngestReadingsRequest;
import com.coldguard.telemetry.grpc.v1.ListConnectivityStatusRequest;
import com.coldguard.telemetry.grpc.v1.ListReadingsRequest;
import com.coldguard.telemetry.grpc.v1.Reading;
import com.coldguard.telemetry.grpc.v1.ReadingResult;
import com.coldguard.telemetry.grpc.v1.ReadingSource;
import com.coldguard.telemetry.grpc.v1.TelemetryServiceGrpc;
import com.coldguard.telemetry.support.InMemoryTelemetryStore;
import com.coldguard.telemetry.support.NoOpTransactionManager;
import com.coldguard.telemetry.support.RecordingEvents;
import com.google.protobuf.Timestamp;
import io.grpc.Context;
import io.grpc.Contexts;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.ServerInterceptors;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.grpc.server.exception.GrpcExceptionHandlerInterceptor;

/**
 * The real service behind the exception-handler interceptor used at runtime, over an in-process
 * transport: statuses, business codes and the identity checks, with nothing mapped by hand.
 */
class TelemetryGrpcServerWiringTest {

  private static final Actor SIMULATOR = Actor.system("sensor-simulator");
  private static final Actor ADMIN = new Actor("admin-1", Set.of(Role.PLATFORM_ADMIN));
  private static final Actor SUPERVISOR = new Actor("sup-1", Set.of(Role.OPERATIONS_SUPERVISOR));
  private static final EvaluationProfile PROFILE =
      new EvaluationProfile(
          new BigDecimal("2"),
          new BigDecimal("8"),
          "CELSIUS",
          BigDecimal.ONE,
          new BigDecimal("3"),
          new BigDecimal("6"),
          3,
          Duration.ofMinutes(5),
          Duration.ofSeconds(5));

  private final AtomicReference<Actor> caller = new AtomicReference<>(SIMULATOR);
  private final InMemoryTelemetryStore store = new InMemoryTelemetryStore();
  private final RecordingEvents events = new RecordingEvents();
  private Server server;
  private ManagedChannel channel;
  private TelemetryServiceGrpc.TelemetryServiceBlockingStub stub;
  private SensorContext sensor;

  @BeforeEach
  void start() throws Exception {
    sensor =
        new SensorContext(
            UUID.randomUUID(), UUID.randomUUID(), Criticality.HIGH, SensorStatus.ACTIVE, PROFILE);
    store.assetContexts.put(sensor.sensorId(), sensor);
    Clock clock = Clock.systemUTC();
    var service =
        new TelemetryGrpcService(
            new IngestReadingsService(
                store.contexts,
                store.readingRepository,
                store.conditionRepository,
                events,
                (source, outcome, eligible, breached) -> {},
                new IngestSettings(3, Duration.ofSeconds(30)),
                clock,
                new NoOpTransactionManager()),
            new ReadingQueryService(store.readingRepository, Duration.ofDays(7), 100, 20));
    String name = "telemetry-wiring-" + UUID.randomUUID();
    server =
        InProcessServerBuilder.forName(name)
            .directExecutor()
            .addService(
                ServerInterceptors.intercept(
                    service,
                    actorFromTestState(),
                    new GrpcExceptionHandlerInterceptor(new TelemetryGrpcExceptionHandler())))
            .build()
            .start();
    channel = InProcessChannelBuilder.forName(name).directExecutor().build();
    stub = TelemetryServiceGrpc.newBlockingStub(channel);
  }

  @AfterEach
  void stop() {
    channel.shutdownNow();
    server.shutdownNow();
  }

  private ServerInterceptor actorFromTestState() {
    return new ServerInterceptor() {
      @Override
      public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
          ServerCall<ReqT, RespT> call, Metadata headers, ServerCallHandler<ReqT, RespT> next) {
        Context context =
            Context.current().withValue(ActorServerInterceptor.ACTOR_CONTEXT_KEY, caller.get());
        return Contexts.interceptCall(context, call, headers, next);
      }
    };
  }

  private static Timestamp ago(long seconds) {
    return Timestamp.newBuilder()
        .setSeconds(Instant.now().minusSeconds(seconds).getEpochSecond())
        .build();
  }

  private Reading reading(double value, long secondsAgo) {
    return Reading.newBuilder()
        .setReadingId(UUID.randomUUID().toString())
        .setSensorId(sensor.sensorId().toString())
        .setRecordedAt(ago(secondsAgo))
        .setValue(value)
        .setUnit("CELSIUS")
        .build();
  }

  private static void assertFails(Runnable call, Status.Code code, String businessCode) {
    assertThatThrownBy(call::run)
        .isInstanceOfSatisfying(
            StatusRuntimeException.class,
            e -> {
              assertThat(e.getStatus().getCode()).isEqualTo(code);
              if (businessCode != null) {
                assertThat(e.getTrailers().get(TelemetryGrpcExceptionHandler.ERROR_CODE))
                    .isEqualTo(businessCode);
              }
            });
  }

  @Test
  void theSimulatorIngestsAndGetsOneResultPerReadingInOrder() {
    Reading inRange = reading(5.0, 10);
    Reading hot = reading(12.0, 5);

    var reply =
        stub.ingestReadings(
            IngestReadingsRequest.newBuilder()
                .setSource(ReadingSource.READING_SOURCE_SIMULATOR)
                .addReadings(inRange)
                .addReadings(hot)
                .build());

    assertThat(reply.getResultsList())
        .extracting(ReadingResult::getReadingId)
        .containsExactly(inRange.getReadingId(), hot.getReadingId());
    assertThat(reply.getResults(0).getOutcome()).isEqualTo(ReadingResult.Outcome.OUTCOME_ACCEPTED);
    assertThat(reply.getResults(0).getEligible()).isTrue();
    assertThat(reply.getResults(0).getBreached()).isFalse();
    assertThat(reply.getResults(1).getBreached()).isTrue();
    assertThat(events.published).hasSize(1);
  }

  @Test
  void resendingReturnsDuplicatesAndAnInvalidReadingCarriesItsRejectionCode() {
    Reading hot = reading(12.0, 5);
    var request =
        IngestReadingsRequest.newBuilder()
            .setSource(ReadingSource.READING_SOURCE_SIMULATOR)
            .addReadings(hot)
            .addReadings(hot.toBuilder().setReadingId("not-a-uuid"))
            .addReadings(reading(5.0, 1).toBuilder().setUnit("KELVIN"))
            .build();

    stub.ingestReadings(request);
    var again = stub.ingestReadings(request);

    assertThat(again.getResults(0).getOutcome()).isEqualTo(ReadingResult.Outcome.OUTCOME_DUPLICATE);
    assertThat(again.getResults(1).getOutcome()).isEqualTo(ReadingResult.Outcome.OUTCOME_REJECTED);
    assertThat(again.getResults(1).getRejectionCode()).isEqualTo("INVALID_READING_ID");
    assertThat(again.getResults(2).getRejectionCode()).isEqualTo("UNIT_MISMATCH");
    assertThat(events.published).hasSize(1);
  }

  @Test
  void aSourceIsRequired() {
    assertFails(
        () ->
            stub.ingestReadings(
                IngestReadingsRequest.newBuilder().addReadings(reading(5, 1)).build()),
        Status.Code.INVALID_ARGUMENT,
        null);
  }

  @Test
  void theSourceMustMatchWhoSendsIt() {
    var simulated =
        IngestReadingsRequest.newBuilder()
            .setSource(ReadingSource.READING_SOURCE_SIMULATOR)
            .addReadings(reading(5, 1))
            .build();
    var injected =
        IngestReadingsRequest.newBuilder()
            .setSource(ReadingSource.READING_SOURCE_TEST_INJECTION)
            .addReadings(reading(5, 1))
            .build();

    caller.set(ADMIN);
    assertFails(() -> stub.ingestReadings(simulated), Status.Code.PERMISSION_DENIED, null);
    assertThat(stub.ingestReadings(injected).getResults(0).getOutcome())
        .isEqualTo(ReadingResult.Outcome.OUTCOME_ACCEPTED);

    caller.set(SIMULATOR);
    assertFails(() -> stub.ingestReadings(injected), Status.Code.PERMISSION_DENIED, null);

    for (Actor denied : new Actor[] {null, SUPERVISOR}) {
      caller.set(denied);
      assertFails(() -> stub.ingestReadings(simulated), Status.Code.PERMISSION_DENIED, null);
    }
  }

  @Test
  void aBatchAboveTheLimitIsInvalidWithItsCode() {
    IngestReadingsRequest.Builder request =
        IngestReadingsRequest.newBuilder().setSource(ReadingSource.READING_SOURCE_SIMULATOR);
    for (int i = 0; i < 4; i++) {
      request.addReadings(reading(5, i + 1));
    }

    assertFails(
        () -> stub.ingestReadings(request.build()),
        Status.Code.INVALID_ARGUMENT,
        "BATCH_TOO_LARGE");
  }

  @Test
  void anUnavailableAssetIsUnavailableToTheProducerWithoutExplainingWhy() {
    store.assetFailure =
        new AssetUnavailableException("connect to asset-service:9091 refused", null);

    assertThatThrownBy(
            () ->
                stub.ingestReadings(
                    IngestReadingsRequest.newBuilder()
                        .setSource(ReadingSource.READING_SOURCE_SIMULATOR)
                        .addReadings(reading(5, 1))
                        .build()))
        .isInstanceOfSatisfying(
            StatusRuntimeException.class,
            e -> {
              assertThat(e.getStatus().getCode()).isEqualTo(Status.Code.UNAVAILABLE);
              assertThat(e.getStatus().getDescription())
                  .doesNotContain("9091")
                  .doesNotContain("refused");
            });
    assertThat(store.readings).isEmpty();
  }

  @Test
  void readingsAreListedPagedNewestFirstForAdministratorsAndSupervisors() {
    stub.ingestReadings(
        IngestReadingsRequest.newBuilder()
            .setSource(ReadingSource.READING_SOURCE_SIMULATOR)
            .addReadings(reading(5, 30))
            .addReadings(reading(12, 20))
            .addReadings(reading(5, 10))
            .build());

    var from = ago(3600);
    var to = ago(-60);
    caller.set(SUPERVISOR);
    var first =
        stub.listReadings(
            ListReadingsRequest.newBuilder()
                .setSensorId(sensor.sensorId().toString())
                .setFrom(from)
                .setTo(to)
                .setPage(com.coldguard.common.grpc.v1.CursorPageRequest.newBuilder().setSize(2))
                .build());
    assertThat(first.getReadingsCount()).isEqualTo(2);
    assertThat(first.getPage().getHasMore()).isTrue();
    var second =
        stub.listReadings(
            ListReadingsRequest.newBuilder()
                .setSensorId(sensor.sensorId().toString())
                .setFrom(from)
                .setTo(to)
                .setPage(
                    com.coldguard.common.grpc.v1.CursorPageRequest.newBuilder()
                        .setSize(2)
                        .setCursor(first.getPage().getNextCursor()))
                .build());

    assertThat(second.getReadingsCount()).isEqualTo(1);
    assertThat(second.getPage().getHasMore()).isFalse();
    assertThat(first.getReadings(0).getRecordedAt().getSeconds())
        .isGreaterThan(first.getReadings(1).getRecordedAt().getSeconds());
    var hot =
        first.getReadingsList().stream().filter(r -> r.getBreached()).findFirst().orElseThrow();
    assertThat(hot.getAnomalyType()).isEqualTo("TEMPERATURE_ABOVE_MAX");
    assertThat(hot.getMagnitude())
        .isEqualTo(com.coldguard.telemetry.grpc.v1.MagnitudeLevel.MAGNITUDE_LEVEL_HIGH);
    assertThat(hot.getSource()).isEqualTo(ReadingSource.READING_SOURCE_SIMULATOR);
  }

  @Test
  void aRangeThatIsMissingTooWideOrForAnInvalidSensorIsRejected() {
    caller.set(ADMIN);
    var valid =
        ListReadingsRequest.newBuilder()
            .setSensorId(sensor.sensorId().toString())
            .setFrom(ago(3600))
            .setTo(ago(0));

    assertFails(
        () -> stub.listReadings(valid.clone().setFrom(ago(8 * 86_400)).build()),
        Status.Code.INVALID_ARGUMENT,
        "RANGE_TOO_WIDE");
    assertFails(
        () -> stub.listReadings(valid.clone().clearTo().build()),
        Status.Code.INVALID_ARGUMENT,
        null);
    assertFails(
        () -> stub.listReadings(valid.clone().setSensorId("nope").build()),
        Status.Code.INVALID_ARGUMENT,
        null);
    assertFails(
        () ->
            stub.listReadings(
                valid
                    .clone()
                    .setPage(
                        com.coldguard.common.grpc.v1.CursorPageRequest.newBuilder()
                            .setCursor("garbage!"))
                    .build()),
        Status.Code.INVALID_ARGUMENT,
        null);
  }

  @Test
  void theSimulatorCannotReadBack() {
    assertFails(
        () ->
            stub.listReadings(
                ListReadingsRequest.newBuilder()
                    .setSensorId(sensor.sensorId().toString())
                    .setFrom(ago(3600))
                    .setTo(ago(0))
                    .build()),
        Status.Code.PERMISSION_DENIED,
        null);
  }

  @Test
  void connectivityStatusIsNotServedYet() {
    caller.set(ADMIN);

    assertFails(
        () -> stub.listConnectivityStatus(ListConnectivityStatusRequest.getDefaultInstance()),
        Status.Code.UNIMPLEMENTED,
        null);
  }
}
