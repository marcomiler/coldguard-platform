package com.coldguard.gateway.api.incident;

import com.coldguard.common.grpc.v1.PageRequest;
import com.coldguard.gateway.api.common.PageResponse;
import com.coldguard.incident.grpc.v1.IncidentView;
import com.coldguard.incident.grpc.v1.ListIncidentsRequest;
import com.google.protobuf.Timestamp;
import java.time.Instant;
import java.util.List;

/** REST ↔ gRPC translation for incidents: renames and converts, decides nothing. */
final class IncidentRestMapper {

  private static final String IMPACT = "IMPACT_";
  private static final String URGENCY = "URGENCY_";

  private IncidentRestMapper() {}

  static ListIncidentsRequest list(
      List<IncidentStatus> statuses,
      List<Priority> priorities,
      String assetId,
      String sensorId,
      Instant createdFrom,
      Instant createdTo,
      int page,
      int size) {
    ListIncidentsRequest.Builder request =
        ListIncidentsRequest.newBuilder()
            .setPage(PageRequest.newBuilder().setPage(page).setSize(size));
    statuses.forEach(
        s -> request.addStatuses(com.coldguard.incident.grpc.v1.IncidentStatus.valueOf(s.name())));
    priorities.forEach(
        p -> request.addPriorities(com.coldguard.incident.grpc.v1.Priority.valueOf(p.name())));
    if (assetId != null) {
      request.setAssetId(assetId);
    }
    if (sensorId != null) {
      request.setSensorId(sensorId);
    }
    if (createdFrom != null) {
      request.setCreatedFrom(timestamp(createdFrom));
    }
    if (createdTo != null) {
      request.setCreatedTo(timestamp(createdTo));
    }
    return request.build();
  }

  static IncidentResponse toRest(IncidentView v) {
    return new IncidentResponse(
        v.getIncidentId(),
        v.getStatus().name(),
        v.getAssetId(),
        v.getSensorId(),
        v.getAnomalyType(),
        strip(v.getImpact().name(), IMPACT),
        strip(v.getUrgency().name(), URGENCY),
        v.getPriority().name(),
        instant(v.hasCreatedAt(), v.getCreatedAt()),
        instant(v.hasAckDueAt(), v.getAckDueAt()),
        instant(v.hasResolveDueAt(), v.getResolveDueAt()),
        instant(v.hasAcknowledgedAt(), v.getAcknowledgedAt()),
        blankToNull(v.getAcknowledgedBy()),
        instant(v.hasLastEscalatedAt(), v.getLastEscalatedAt()),
        v.getEscalationCount(),
        instant(v.hasClosedAt(), v.getClosedAt()),
        blankToNull(v.getClosedBy()),
        blankToNull(v.getCause()),
        blankToNull(v.getResolutionComment()),
        v.getOccurrenceCount(),
        instant(v.hasLastOccurrenceAt(), v.getLastOccurrenceAt()));
  }

  static PageResponse.PageInfo toRest(com.coldguard.common.grpc.v1.PageInfo page) {
    return new PageResponse.PageInfo(
        page.getPage(), page.getSize(), page.getTotalElements(), page.getTotalPages());
  }

  private static Timestamp timestamp(Instant instant) {
    return Timestamp.newBuilder()
        .setSeconds(instant.getEpochSecond())
        .setNanos(instant.getNano())
        .build();
  }

  private static Instant instant(boolean present, Timestamp timestamp) {
    return present ? Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos()) : null;
  }

  private static String strip(String value, String prefix) {
    return value.startsWith(prefix) ? value.substring(prefix.length()) : value;
  }

  private static String blankToNull(String value) {
    return value.isEmpty() ? null : value;
  }
}
