package com.coldguard.incident.api;

import com.coldguard.common.grpc.v1.PageInfo;
import com.coldguard.common.grpc.v1.PageRequest;
import com.coldguard.incident.application.IncidentSearch;
import com.coldguard.incident.application.OpenIncidentCommand;
import com.coldguard.incident.application.PageQuery;
import com.coldguard.incident.application.PageResult;
import com.coldguard.incident.domain.Criticality;
import com.coldguard.incident.domain.Impact;
import com.coldguard.incident.domain.Incident;
import com.coldguard.incident.domain.IncidentStatus;
import com.coldguard.incident.domain.Magnitude;
import com.coldguard.incident.domain.Priority;
import com.coldguard.incident.domain.Urgency;
import com.coldguard.incident.grpc.v1.CloseIncidentResponse;
import com.coldguard.incident.grpc.v1.CreateIncidentRequest;
import com.coldguard.incident.grpc.v1.CreateIncidentResponse;
import com.coldguard.incident.grpc.v1.IncidentView;
import com.coldguard.incident.grpc.v1.ListIncidentsRequest;
import com.coldguard.incident.grpc.v1.ListIncidentsResponse;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;

/** Translates between the gRPC messages and the application's commands and the domain model. */
final class IncidentGrpcMapper {

  private IncidentGrpcMapper() {}

  static OpenIncidentCommand toOpenCommand(CreateIncidentRequest request) {
    return new OpenIncidentCommand(
        request.getAssetId(),
        criticality(request.getAssetCriticality()),
        request.getSensorId(),
        request.getAnomalyType(),
        magnitude(request.getMagnitude()),
        request.getPersistent(),
        null);
  }

  static CreateIncidentResponse toCreateResponse(Incident incident) {
    return CreateIncidentResponse.newBuilder()
        .setIncidentId(incident.id())
        .setStatus(status(incident.status()))
        .setImpact(impact(incident.impact()))
        .setUrgency(urgency(incident.urgency()))
        .setPriority(priority(incident.priority()))
        .setCreatedAt(DateTimeFormatter.ISO_INSTANT.format(incident.createdAt()))
        .setIncident(toView(incident))
        .build();
  }

  static CloseIncidentResponse toCloseResponse(Incident incident) {
    return CloseIncidentResponse.newBuilder()
        .setIncidentId(incident.id())
        .setStatus(status(incident.status()))
        .setClosedAt(DateTimeFormatter.ISO_INSTANT.format(incident.closedAt()))
        .setIncident(toView(incident))
        .build();
  }

  static IncidentView toView(Incident i) {
    IncidentView.Builder view =
        IncidentView.newBuilder()
            .setIncidentId(i.id())
            .setStatus(status(i.status()))
            .setAssetId(i.assetId())
            .setSensorId(i.sensorId())
            .setAnomalyType(i.anomalyType())
            .setImpact(impact(i.impact()))
            .setUrgency(urgency(i.urgency()))
            .setPriority(priority(i.priority()))
            .setCreatedAt(timestamp(i.createdAt()))
            .setEscalationCount(i.escalationCount())
            .setOccurrenceCount(i.occurrenceCount());
    if (i.ackDueAt() != null) {
      view.setAckDueAt(timestamp(i.ackDueAt()));
    }
    if (i.resolveDueAt() != null) {
      view.setResolveDueAt(timestamp(i.resolveDueAt()));
    }
    if (i.acknowledgedAt() != null) {
      view.setAcknowledgedAt(timestamp(i.acknowledgedAt()));
    }
    if (i.acknowledgedBy() != null) {
      view.setAcknowledgedBy(i.acknowledgedBy());
    }
    if (i.lastEscalatedAt() != null) {
      view.setLastEscalatedAt(timestamp(i.lastEscalatedAt()));
    }
    if (i.closedAt() != null) {
      view.setClosedAt(timestamp(i.closedAt()));
    }
    if (i.closedBy() != null) {
      view.setClosedBy(i.closedBy());
    }
    if (i.cause() != null) {
      view.setCause(i.cause());
    }
    if (i.resolutionComment() != null) {
      view.setResolutionComment(i.resolutionComment());
    }
    if (i.lastOccurrenceAt() != null) {
      view.setLastOccurrenceAt(timestamp(i.lastOccurrenceAt()));
    }
    return view.build();
  }

  static IncidentSearch toSearch(ListIncidentsRequest request) {
    return new IncidentSearch(
        request.getStatusesList().stream()
            .map(IncidentGrpcMapper::status)
            .collect(Collectors.toSet()),
        request.getPrioritiesList().stream()
            .map(IncidentGrpcMapper::priority)
            .collect(Collectors.toSet()),
        request.getAssetId(),
        request.getSensorId(),
        request.hasCreatedFrom() ? instant(request.getCreatedFrom()) : null,
        request.hasCreatedTo() ? instant(request.getCreatedTo()) : null);
  }

  static PageQuery toPageQuery(PageRequest page) {
    return new PageQuery(page.getPage(), page.getSize());
  }

  static ListIncidentsResponse toListResponse(PageResult<Incident> result) {
    return ListIncidentsResponse.newBuilder()
        .addAllIncidents(result.items().stream().map(IncidentGrpcMapper::toView).toList())
        .setPage(
            PageInfo.newBuilder()
                .setPage(result.page())
                .setSize(result.size())
                .setTotalElements(result.totalElements())
                .setTotalPages(result.totalPages()))
        .build();
  }

  private static Timestamp timestamp(Instant instant) {
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }

  private static Instant instant(Timestamp timestamp) {
    return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
  }

  private static Criticality criticality(com.coldguard.incident.grpc.v1.Criticality criticality) {
    return switch (criticality) {
      case CRITICALITY_LOW -> Criticality.LOW;
      case CRITICALITY_MEDIUM -> Criticality.MEDIUM;
      case CRITICALITY_HIGH -> Criticality.HIGH;
      case CRITICALITY_CRITICAL -> Criticality.CRITICAL;
      case CRITICALITY_UNSPECIFIED, UNRECOGNIZED ->
          throw new IllegalArgumentException("asset_criticality is required");
    };
  }

  private static Magnitude magnitude(com.coldguard.incident.grpc.v1.Magnitude magnitude) {
    return switch (magnitude) {
      case MAGNITUDE_LOW -> Magnitude.LOW;
      case MAGNITUDE_MEDIUM -> Magnitude.MEDIUM;
      case MAGNITUDE_HIGH -> Magnitude.HIGH;
      case MAGNITUDE_CRITICAL -> Magnitude.CRITICAL;
      case MAGNITUDE_UNSPECIFIED, UNRECOGNIZED ->
          throw new IllegalArgumentException("magnitude is required");
    };
  }

  private static com.coldguard.incident.grpc.v1.Impact impact(Impact impact) {
    return switch (impact) {
      case LOW -> com.coldguard.incident.grpc.v1.Impact.IMPACT_LOW;
      case MEDIUM -> com.coldguard.incident.grpc.v1.Impact.IMPACT_MEDIUM;
      case HIGH -> com.coldguard.incident.grpc.v1.Impact.IMPACT_HIGH;
      case CRITICAL -> com.coldguard.incident.grpc.v1.Impact.IMPACT_CRITICAL;
    };
  }

  private static com.coldguard.incident.grpc.v1.Urgency urgency(Urgency urgency) {
    return switch (urgency) {
      case LOW -> com.coldguard.incident.grpc.v1.Urgency.URGENCY_LOW;
      case MEDIUM -> com.coldguard.incident.grpc.v1.Urgency.URGENCY_MEDIUM;
      case HIGH -> com.coldguard.incident.grpc.v1.Urgency.URGENCY_HIGH;
      case IMMEDIATE -> com.coldguard.incident.grpc.v1.Urgency.URGENCY_IMMEDIATE;
    };
  }

  private static com.coldguard.incident.grpc.v1.Priority priority(Priority priority) {
    return switch (priority) {
      case P1 -> com.coldguard.incident.grpc.v1.Priority.P1;
      case P2 -> com.coldguard.incident.grpc.v1.Priority.P2;
      case P3 -> com.coldguard.incident.grpc.v1.Priority.P3;
      case P4 -> com.coldguard.incident.grpc.v1.Priority.P4;
    };
  }

  private static Priority priority(com.coldguard.incident.grpc.v1.Priority priority) {
    return switch (priority) {
      case P1 -> Priority.P1;
      case P2 -> Priority.P2;
      case P3 -> Priority.P3;
      case P4 -> Priority.P4;
      case PRIORITY_UNSPECIFIED, UNRECOGNIZED ->
          throw new IllegalArgumentException("priority filter has an unknown value");
    };
  }

  static com.coldguard.incident.grpc.v1.IncidentStatus status(IncidentStatus status) {
    return switch (status) {
      case CREATED -> com.coldguard.incident.grpc.v1.IncidentStatus.CREATED;
      case ACKNOWLEDGED -> com.coldguard.incident.grpc.v1.IncidentStatus.ACKNOWLEDGED;
      case ESCALATED -> com.coldguard.incident.grpc.v1.IncidentStatus.ESCALATED;
      case CLOSED -> com.coldguard.incident.grpc.v1.IncidentStatus.CLOSED;
    };
  }

  private static IncidentStatus status(com.coldguard.incident.grpc.v1.IncidentStatus status) {
    return switch (status) {
      case CREATED -> IncidentStatus.CREATED;
      case ACKNOWLEDGED -> IncidentStatus.ACKNOWLEDGED;
      case ESCALATED -> IncidentStatus.ESCALATED;
      case CLOSED -> IncidentStatus.CLOSED;
      case INCIDENT_STATUS_UNSPECIFIED, UNRECOGNIZED ->
          throw new IllegalArgumentException("status filter has an unknown value");
    };
  }
}
