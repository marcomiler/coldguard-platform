package com.coldguard.telemetry.infrastructure;

import com.coldguard.asset.grpc.v1.AssetServiceGrpc;
import com.coldguard.asset.grpc.v1.GetSensorEvaluationContextsRequest;
import com.coldguard.asset.grpc.v1.GetSensorEvaluationContextsResponse;
import com.coldguard.asset.grpc.v1.MagnitudeBands;
import com.coldguard.asset.grpc.v1.OperationalProfile;
import com.coldguard.asset.grpc.v1.PersistenceWindow;
import com.coldguard.asset.grpc.v1.SensorEvaluationContext;
import com.coldguard.asset.grpc.v1.SensorStatus;
import com.coldguard.common.grpc.v1.Criticality;
import com.google.protobuf.Duration;
import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** A scriptable Asset over a real (in-process) gRPC transport. */
class FakeAsset implements AutoCloseable {

  /** What Asset "knows", keyed by sensor id; a sensor not in the map does not exist. */
  final Map<String, SensorEvaluationContext> sensors = new HashMap<>();

  /** The ids asked for, one list per call. */
  final List<List<String>> calls = new ArrayList<>();

  Status failure;
  boolean neverAnswers;

  private final Server server;
  private final ManagedChannel channel;
  final AssetServiceGrpc.AssetServiceBlockingStub stub;

  FakeAsset() throws Exception {
    String name = "fake-asset-" + UUID.randomUUID();
    server =
        InProcessServerBuilder.forName(name)
            .addService(
                new AssetServiceGrpc.AssetServiceImplBase() {
                  @Override
                  public void getSensorEvaluationContexts(
                      GetSensorEvaluationContextsRequest request,
                      StreamObserver<GetSensorEvaluationContextsResponse> observer) {
                    calls.add(new ArrayList<>(request.getSensorIdsList()));
                    if (neverAnswers) {
                      return;
                    }
                    if (failure != null) {
                      observer.onError(failure.asRuntimeException());
                      return;
                    }
                    GetSensorEvaluationContextsResponse.Builder reply =
                        GetSensorEvaluationContextsResponse.newBuilder();
                    request
                        .getSensorIdsList()
                        .forEach(
                            id -> {
                              if (sensors.containsKey(id)) {
                                reply.addContexts(sensors.get(id));
                              }
                            });
                    observer.onNext(reply.build());
                    observer.onCompleted();
                  }
                })
            .build()
            .start();
    channel = InProcessChannelBuilder.forName(name).build();
    stub = AssetServiceGrpc.newBlockingStub(channel);
  }

  /** An ACTIVE sensor of a HIGH-criticality asset with the usual 2 to 8 profile. */
  String addSensor(SensorStatus status, boolean withProfile) {
    String id = UUID.randomUUID().toString();
    SensorEvaluationContext.Builder context =
        SensorEvaluationContext.newBuilder()
            .setSensorId(id)
            .setAssetId(UUID.randomUUID().toString())
            .setAssetCriticality(Criticality.CRITICALITY_HIGH)
            .setStatus(status);
    if (withProfile) {
      context.setProfile(
          OperationalProfile.newBuilder()
              .setSensorId(id)
              .setMinTemperature(2)
              .setMaxTemperature(8)
              .setUnit("CELSIUS")
              .setMagnitudeBands(
                  MagnitudeBands.newBuilder().setMediumFrom(1).setHighFrom(3).setCriticalFrom(6))
              .setPersistence(
                  PersistenceWindow.newBuilder()
                      .setMinConsecutiveBreaches(3)
                      .setWindow(Duration.newBuilder().setSeconds(300)))
              .setExpectedReadingInterval(Duration.newBuilder().setSeconds(5))
              .setVersion(1));
    }
    sensors.put(id, context.build());
    return id;
  }

  @Override
  public void close() throws Exception {
    channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
    server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
  }
}
