package com.coldguard.gateway.api;

import com.coldguard.commons.correlation.CorrelationContext;
import com.coldguard.gateway.infrastructure.IncidentGrpcClient;
import com.coldguard.gateway.infrastructure.InvalidIncidentRequestException;
import com.coldguard.incident.grpc.v1.CloseIncidentRequest;
import com.coldguard.incident.grpc.v1.CloseIncidentResponse;
import com.coldguard.incident.grpc.v1.CreateIncidentRequest;
import com.coldguard.incident.grpc.v1.CreateIncidentResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/incidents")
public class IncidentController {

  private final IncidentGrpcClient incidentGrpcClient;

  public IncidentController(IncidentGrpcClient incidentGrpcClient) {
    this.incidentGrpcClient = incidentGrpcClient;
  }

  @PostMapping
  public ResponseEntity<CreateIncidentHttpResponse> createIncident(
      @RequestBody CreateIncidentHttpRequest request) {
    CreateIncidentResponse grpcResponse = incidentGrpcClient.createIncident(toGrpcRequest(request));
    return ResponseEntity.status(HttpStatus.CREATED).body(toHttpResponse(grpcResponse));
  }

  @PostMapping("/{incidentId}/close")
  public ResponseEntity<CloseIncidentHttpResponse> closeIncident(
      @PathVariable String incidentId, @RequestBody CloseIncidentHttpRequest request) {
    CloseIncidentResponse grpcResponse =
        incidentGrpcClient.closeIncident(toGrpcCloseRequest(incidentId, request));
    return ResponseEntity.ok(toCloseHttpResponse(grpcResponse));
  }

  private static CloseIncidentRequest toGrpcCloseRequest(
      String incidentId, CloseIncidentHttpRequest request) {
    return CloseIncidentRequest.newBuilder()
        .setIncidentId(incidentId)
        .setCause(request.cause() == null ? "" : request.cause())
        .setResolutionComment(
            request.resolutionComment() == null ? "" : request.resolutionComment())
        .build();
  }

  private static CloseIncidentHttpResponse toCloseHttpResponse(CloseIncidentResponse response) {
    return new CloseIncidentHttpResponse(
        response.getIncidentId(), response.getStatus(), response.getClosedAt());
  }

  private static CreateIncidentRequest toGrpcRequest(CreateIncidentHttpRequest request) {
    return CreateIncidentRequest.newBuilder()
        .setAssetId(request.assetId())
        .setAssetCriticality(
            requireNonNull(request.assetCriticality(), "assetCriticality is required"))
        .setSensorId(request.sensorId())
        .setAnomalyType(request.anomalyType())
        .setMagnitude(requireNonNull(request.magnitude(), "magnitude is required"))
        .setPersistent(request.persistent())
        .setCorrelationId(
            request.correlationId() == null
                ? CorrelationContext.current().orElse("")
                : request.correlationId())
        .build();
  }

  private static <T> T requireNonNull(T value, String message) {
    if (value == null) {
      throw new InvalidIncidentRequestException(message, null);
    }
    return value;
  }

  private static CreateIncidentHttpResponse toHttpResponse(CreateIncidentResponse response) {
    return new CreateIncidentHttpResponse(
        response.getIncidentId(),
        response.getStatus(),
        response.getImpact(),
        response.getUrgency(),
        response.getPriority(),
        response.getCreatedAt());
  }
}
