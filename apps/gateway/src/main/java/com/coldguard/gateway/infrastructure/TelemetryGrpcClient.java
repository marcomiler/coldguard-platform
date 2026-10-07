package com.coldguard.gateway.infrastructure;

import com.coldguard.gateway.config.DownstreamProperties;
import com.coldguard.telemetry.grpc.v1.IngestReadingsRequest;
import com.coldguard.telemetry.grpc.v1.IngestReadingsResponse;
import com.coldguard.telemetry.grpc.v1.ListConnectivityStatusRequest;
import com.coldguard.telemetry.grpc.v1.ListConnectivityStatusResponse;
import com.coldguard.telemetry.grpc.v1.ListReadingsRequest;
import com.coldguard.telemetry.grpc.v1.ListReadingsResponse;
import com.coldguard.telemetry.grpc.v1.TelemetryServiceGrpc;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Thin adapter over the Telemetry Service stub. Every call goes through {@link GrpcInvoker}
 * (deadline and error translation); the caller's identity travels as metadata, never as an
 * argument.
 */
@Component
public class TelemetryGrpcClient {

  static final String SERVICE = "telemetry-service";

  private final TelemetryServiceGrpc.TelemetryServiceBlockingStub stub;
  private final GrpcInvoker invoker;
  private final DownstreamProperties properties;

  public TelemetryGrpcClient(
      TelemetryServiceGrpc.TelemetryServiceBlockingStub stub,
      GrpcInvoker invoker,
      DownstreamProperties properties) {
    this.stub = stub;
    this.invoker = invoker;
    this.properties = properties;
  }

  private <R> R query(Function<TelemetryServiceGrpc.TelemetryServiceBlockingStub, R> call) {
    return invoker.query(SERVICE, stub, properties.deadline(SERVICE), call);
  }

  private <R> R call(Function<TelemetryServiceGrpc.TelemetryServiceBlockingStub, R> call) {
    return invoker.call(SERVICE, stub, properties.deadline(SERVICE), call);
  }

  public IngestReadingsResponse ingestReadings(IngestReadingsRequest request) {
    return call(s -> s.ingestReadings(request));
  }

  public ListReadingsResponse listReadings(ListReadingsRequest request) {
    return query(s -> s.listReadings(request));
  }

  public ListConnectivityStatusResponse listConnectivityStatus(
      ListConnectivityStatusRequest request) {
    return query(s -> s.listConnectivityStatus(request));
  }
}
