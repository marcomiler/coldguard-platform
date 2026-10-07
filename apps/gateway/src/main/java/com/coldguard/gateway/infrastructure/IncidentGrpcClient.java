package com.coldguard.gateway.infrastructure;

import com.coldguard.gateway.config.DownstreamProperties;
import com.coldguard.incident.grpc.v1.AcknowledgeIncidentRequest;
import com.coldguard.incident.grpc.v1.CloseIncidentRequest;
import com.coldguard.incident.grpc.v1.CloseIncidentResponse;
import com.coldguard.incident.grpc.v1.CreateIncidentRequest;
import com.coldguard.incident.grpc.v1.CreateIncidentResponse;
import com.coldguard.incident.grpc.v1.EscalateIncidentRequest;
import com.coldguard.incident.grpc.v1.GetIncidentRequest;
import com.coldguard.incident.grpc.v1.IncidentServiceGrpc;
import com.coldguard.incident.grpc.v1.IncidentView;
import com.coldguard.incident.grpc.v1.ListIncidentsRequest;
import com.coldguard.incident.grpc.v1.ListIncidentsResponse;
import java.util.function.Function;
import org.springframework.stereotype.Component;

/**
 * Incident Service: queries and lifecycle commands. Every call goes through {@link GrpcInvoker};
 * the caller's identity travels as metadata, added by the global interceptor.
 */
@Component
public class IncidentGrpcClient {

  static final String SERVICE = "incident-service";

  private final IncidentServiceGrpc.IncidentServiceBlockingStub stub;
  private final GrpcInvoker invoker;
  private final DownstreamProperties properties;

  public IncidentGrpcClient(
      IncidentServiceGrpc.IncidentServiceBlockingStub stub,
      GrpcInvoker invoker,
      DownstreamProperties properties) {
    this.stub = stub;
    this.invoker = invoker;
    this.properties = properties;
  }

  private <R> R call(Function<IncidentServiceGrpc.IncidentServiceBlockingStub, R> call) {
    return invoker.call(SERVICE, stub, properties.deadline(SERVICE), call);
  }

  private <R> R query(Function<IncidentServiceGrpc.IncidentServiceBlockingStub, R> call) {
    return invoker.query(SERVICE, stub, properties.deadline(SERVICE), call);
  }

  public IncidentView getIncident(GetIncidentRequest request) {
    return query(s -> s.getIncident(request));
  }

  public ListIncidentsResponse listIncidents(ListIncidentsRequest request) {
    return query(s -> s.listIncidents(request));
  }

  public CreateIncidentResponse createIncident(CreateIncidentRequest request) {
    return call(s -> s.createIncident(request));
  }

  public CloseIncidentResponse closeIncident(CloseIncidentRequest request) {
    return call(s -> s.closeIncident(request));
  }

  public IncidentView acknowledgeIncident(AcknowledgeIncidentRequest request) {
    return call(s -> s.acknowledgeIncident(request));
  }

  public IncidentView escalateIncident(EscalateIncidentRequest request) {
    return call(s -> s.escalateIncident(request));
  }
}
