package com.coldguard.incident.api;

import com.coldguard.incident.application.CloseIncidentCommand;
import com.coldguard.incident.application.CloseIncidentService;
import com.coldguard.incident.application.CreateIncidentCommand;
import com.coldguard.incident.application.CreateIncidentService;
import com.coldguard.incident.domain.Criticality;
import com.coldguard.incident.domain.Impact;
import com.coldguard.incident.domain.Incident;
import com.coldguard.incident.domain.IncidentStatus;
import com.coldguard.incident.domain.Magnitude;
import com.coldguard.incident.domain.Priority;
import com.coldguard.incident.domain.Urgency;
import com.coldguard.incident.grpc.v1.CloseIncidentRequest;
import com.coldguard.incident.grpc.v1.CloseIncidentResponse;
import com.coldguard.incident.grpc.v1.CreateIncidentRequest;
import com.coldguard.incident.grpc.v1.CreateIncidentResponse;
import com.coldguard.incident.grpc.v1.IncidentServiceGrpc;
import com.coldguard.incident.identity.api.ActorServerInterceptor;
import io.grpc.stub.StreamObserver;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import org.springframework.grpc.server.service.GrpcService;

/**
 * gRPC endpoint for incident creation and closing
 * (contracts/grpc/incident/v1/incident_service.proto). Exceptions are mapped to gRPC statuses by
 * the registered {@link IncidentGrpcExceptionHandler}; the correlation id is placed in the MDC by
 * the shared server interceptor.
 */
@GrpcService
public class IncidentGrpcService extends IncidentServiceGrpc.IncidentServiceImplBase {

  private final CreateIncidentService createIncidentService;
  private final CloseIncidentService closeIncidentService;

  public IncidentGrpcService(
      CreateIncidentService createIncidentService, CloseIncidentService closeIncidentService) {
    this.createIncidentService = createIncidentService;
    this.closeIncidentService = closeIncidentService;
  }

  @Override
  public void createIncident(
      CreateIncidentRequest request, StreamObserver<CreateIncidentResponse> responseObserver) {
    Incident incident = createIncidentService.create(toCommand(request));
    responseObserver.onNext(toResponse(incident));
    responseObserver.onCompleted();
  }

  @Override
  public void closeIncident(
      CloseIncidentRequest request, StreamObserver<CloseIncidentResponse> responseObserver) {
    CloseIncidentCommand command =
        new CloseIncidentCommand(
            request.getIncidentId(),
            request.getCause(),
            request.getResolutionComment(),
            ActorServerInterceptor.ACTOR_CONTEXT_KEY.get());
    Incident incident = closeIncidentService.close(command);
    responseObserver.onNext(toCloseResponse(incident));
    responseObserver.onCompleted();
  }

  /**
   * closed_at is not persisted (Incident has no such column yet): it reflects this response's build
   * time, not a stored timestamp. Re-querying the incident later cannot recover it.
   */
  private CloseIncidentResponse toCloseResponse(Incident incident) {
    return CloseIncidentResponse.newBuilder()
        .setIncidentId(incident.id())
        .setStatus(toGrpcStatus(incident.status()))
        .setClosedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now()))
        .build();
  }

  private CreateIncidentCommand toCommand(CreateIncidentRequest request) {
    return new CreateIncidentCommand(
        request.getAssetId(),
        toDomainCriticality(request.getAssetCriticality()),
        request.getSensorId(),
        request.getAnomalyType(),
        toDomainMagnitude(request.getMagnitude()),
        request.getPersistent(),
        request.getCorrelationId());
  }

  private CreateIncidentResponse toResponse(Incident incident) {
    return CreateIncidentResponse.newBuilder()
        .setIncidentId(incident.id())
        .setStatus(toGrpcStatus(incident.status()))
        .setImpact(toGrpcImpact(incident.impact()))
        .setUrgency(toGrpcUrgency(incident.urgency()))
        .setPriority(toGrpcPriority(incident.priority()))
        .setCreatedAt(DateTimeFormatter.ISO_INSTANT.format(incident.createdAt()))
        .build();
  }

  private static Criticality toDomainCriticality(
      com.coldguard.incident.grpc.v1.Criticality criticality) {
    return switch (criticality) {
      case CRITICALITY_LOW -> Criticality.LOW;
      case CRITICALITY_MEDIUM -> Criticality.MEDIUM;
      case CRITICALITY_HIGH -> Criticality.HIGH;
      case CRITICALITY_CRITICAL -> Criticality.CRITICAL;
      case CRITICALITY_UNSPECIFIED, UNRECOGNIZED ->
          throw new IllegalArgumentException("asset_criticality is required");
    };
  }

  private static Magnitude toDomainMagnitude(com.coldguard.incident.grpc.v1.Magnitude magnitude) {
    return switch (magnitude) {
      case MAGNITUDE_LOW -> Magnitude.LOW;
      case MAGNITUDE_MEDIUM -> Magnitude.MEDIUM;
      case MAGNITUDE_HIGH -> Magnitude.HIGH;
      case MAGNITUDE_CRITICAL -> Magnitude.CRITICAL;
      case MAGNITUDE_UNSPECIFIED, UNRECOGNIZED ->
          throw new IllegalArgumentException("magnitude is required");
    };
  }

  private static com.coldguard.incident.grpc.v1.Impact toGrpcImpact(Impact impact) {
    return switch (impact) {
      case LOW -> com.coldguard.incident.grpc.v1.Impact.IMPACT_LOW;
      case MEDIUM -> com.coldguard.incident.grpc.v1.Impact.IMPACT_MEDIUM;
      case HIGH -> com.coldguard.incident.grpc.v1.Impact.IMPACT_HIGH;
      case CRITICAL -> com.coldguard.incident.grpc.v1.Impact.IMPACT_CRITICAL;
    };
  }

  private static com.coldguard.incident.grpc.v1.Urgency toGrpcUrgency(Urgency urgency) {
    return switch (urgency) {
      case LOW -> com.coldguard.incident.grpc.v1.Urgency.URGENCY_LOW;
      case MEDIUM -> com.coldguard.incident.grpc.v1.Urgency.URGENCY_MEDIUM;
      case HIGH -> com.coldguard.incident.grpc.v1.Urgency.URGENCY_HIGH;
      case IMMEDIATE -> com.coldguard.incident.grpc.v1.Urgency.URGENCY_IMMEDIATE;
    };
  }

  private static com.coldguard.incident.grpc.v1.Priority toGrpcPriority(Priority priority) {
    return switch (priority) {
      case P1 -> com.coldguard.incident.grpc.v1.Priority.P1;
      case P2 -> com.coldguard.incident.grpc.v1.Priority.P2;
      case P3 -> com.coldguard.incident.grpc.v1.Priority.P3;
      case P4 -> com.coldguard.incident.grpc.v1.Priority.P4;
    };
  }

  static com.coldguard.incident.grpc.v1.IncidentStatus toGrpcStatus(IncidentStatus status) {
    return switch (status) {
      case CREATED -> com.coldguard.incident.grpc.v1.IncidentStatus.CREATED;
      case CLOSED -> com.coldguard.incident.grpc.v1.IncidentStatus.CLOSED;
    };
  }
}
