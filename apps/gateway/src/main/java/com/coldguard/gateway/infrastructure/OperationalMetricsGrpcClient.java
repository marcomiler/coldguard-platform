package com.coldguard.gateway.infrastructure;

import com.coldguard.gateway.config.DownstreamProperties;
import com.coldguard.metrics.grpc.v1.GetIncidentMetricsRequest;
import com.coldguard.metrics.grpc.v1.GetIncidentMetricsResponse;
import com.coldguard.metrics.grpc.v1.OperationalMetricsServiceGrpc;
import org.springframework.stereotype.Component;

@Component
public class OperationalMetricsGrpcClient {

  private static final String SERVICE = "incident-service";

  private final OperationalMetricsServiceGrpc.OperationalMetricsServiceBlockingStub stub;
  private final GrpcInvoker invoker;
  private final DownstreamProperties properties;

  public OperationalMetricsGrpcClient(
      OperationalMetricsServiceGrpc.OperationalMetricsServiceBlockingStub stub,
      GrpcInvoker invoker,
      DownstreamProperties properties) {
    this.stub = stub;
    this.invoker = invoker;
    this.properties = properties;
  }

  public GetIncidentMetricsResponse getIncidentMetrics(GetIncidentMetricsRequest request) {
    return invoker.call(
        SERVICE, stub, properties.deadline(SERVICE), s -> s.getIncidentMetrics(request));
  }
}
