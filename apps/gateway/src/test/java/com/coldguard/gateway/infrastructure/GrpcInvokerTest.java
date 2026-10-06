package com.coldguard.gateway.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.asset.grpc.v1.AssetServiceGrpc;
import com.coldguard.asset.grpc.v1.GetSensorRequest;
import com.coldguard.asset.grpc.v1.Sensor;
import com.coldguard.gateway.config.DownstreamProperties;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The invoker and the Asset client against a real (in-process) gRPC server. */
class GrpcInvokerTest {

  private Server server;
  private ManagedChannel channel;

  @AfterEach
  void tearDown() throws InterruptedException {
    if (channel != null) {
      channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
    if (server != null) {
      server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    }
  }

  private AssetGrpcClient clientAnswering(
      java.util.function.Consumer<StreamObserver<Sensor>> behaviour, Duration deadline)
      throws Exception {
    String name = "invoker-" + UUID.randomUUID();
    server =
        InProcessServerBuilder.forName(name)
            .addService(
                new AssetServiceGrpc.AssetServiceImplBase() {
                  @Override
                  public void getSensor(GetSensorRequest request, StreamObserver<Sensor> observer) {
                    behaviour.accept(observer);
                  }
                })
            .build()
            .start();
    channel = InProcessChannelBuilder.forName(name).build();
    return new AssetGrpcClient(
        AssetServiceGrpc.newBlockingStub(channel),
        new GrpcInvoker(),
        new DownstreamProperties(
            Map.of("asset-service", new DownstreamProperties.Service(deadline))));
  }

  private static GetSensorRequest request() {
    return GetSensorRequest.newBuilder().setSensorId("s-1").build();
  }

  @Test
  void aSuccessfulCallReturnsTheReply() throws Exception {
    var client =
        clientAnswering(
            observer -> {
              observer.onNext(Sensor.newBuilder().setId("s-1").build());
              observer.onCompleted();
            },
            Duration.ofSeconds(5));

    assertThat(client.getSensor(request()).getId()).isEqualTo("s-1");
  }

  @Test
  void aFailureCarriesItsStatusItsPublishedCodeAndItsDescription() throws Exception {
    var client =
        clientAnswering(
            observer -> {
              Metadata trailers = new Metadata();
              trailers.put(GrpcInvoker.ERROR_CODE, "SENSOR_NOT_FOUND");
              observer.onError(
                  Status.NOT_FOUND
                      .withDescription("Sensor not found: s-1")
                      .asRuntimeException(trailers));
            },
            Duration.ofSeconds(5));

    assertThatThrownBy(() -> client.getSensor(request()))
        .isInstanceOfSatisfying(
            DownstreamCallException.class,
            e -> {
              assertThat(e.grpcCode()).isEqualTo(Status.Code.NOT_FOUND);
              assertThat(e.errorCode()).isEqualTo("SENSOR_NOT_FOUND");
              assertThat(e.getMessage()).isEqualTo("Sensor not found: s-1");
              assertThat(e.service()).isEqualTo("asset-service");
            });
  }

  @Test
  void aFailureWithoutATrailerHasNoPublishedCode() throws Exception {
    var client =
        clientAnswering(
            observer ->
                observer.onError(
                    Status.INVALID_ARGUMENT.withDescription("bad").asRuntimeException()),
            Duration.ofSeconds(5));

    assertThatThrownBy(() -> client.getSensor(request()))
        .isInstanceOfSatisfying(
            DownstreamCallException.class, e -> assertThat(e.errorCode()).isNull());
  }

  @Test
  void aSlowServiceIsCutOffAtTheConfiguredDeadline() throws Exception {
    var client =
        clientAnswering(
            observer -> {
              // never answers
            },
            Duration.ofMillis(300));
    long started = System.nanoTime();

    assertThatThrownBy(() -> client.getSensor(request()))
        .isInstanceOfSatisfying(
            DownstreamCallException.class,
            e -> assertThat(e.grpcCode()).isEqualTo(Status.Code.DEADLINE_EXCEEDED));
    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
  }

  @Test
  void theDeadlineDefaultsToFiveSecondsAndIsConfiguredPerService() {
    var properties =
        new DownstreamProperties(
            Map.of("asset-service", new DownstreamProperties.Service(Duration.ofSeconds(2))));

    assertThat(properties.deadline("asset-service")).isEqualTo(Duration.ofSeconds(2));
    assertThat(properties.deadline("telemetry-service")).isEqualTo(Duration.ofSeconds(5));
    assertThat(
            new DownstreamProperties(Map.of("x", new DownstreamProperties.Service(null)))
                .deadline("x"))
        .isEqualTo(Duration.ofSeconds(5));
  }
}
