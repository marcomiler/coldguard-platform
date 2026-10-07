package com.coldguard.incident.infrastructure;

import com.coldguard.incident.domain.Incident;
import java.util.UUID;

/** The single place where the incident domain model and its table row are translated. */
final class IncidentPersistenceMapper {

  private IncidentPersistenceMapper() {}

  static IncidentEntity toNewEntity(Incident incident) {
    IncidentEntity entity = new IncidentEntity();
    entity.id = UUID.fromString(incident.id());
    entity.assetId = incident.assetId();
    entity.sensorId = incident.sensorId();
    entity.anomalyType = incident.anomalyType();
    entity.createdAt = incident.createdAt();
    entity.impact = incident.impact();
    entity.sourceReadingId =
        incident.sourceReadingId() == null ? null : UUID.fromString(incident.sourceReadingId());
    copyState(incident, entity);
    return entity;
  }

  /** Copies everything a transition can change; identity and creation facts stay as stored. */
  static void copyState(Incident incident, IncidentEntity entity) {
    entity.urgency = incident.urgency();
    entity.priority = incident.priority();
    entity.status = incident.status();
    entity.ackDueAt = incident.ackDueAt();
    entity.resolveDueAt = incident.resolveDueAt();
    entity.acknowledgedAt = incident.acknowledgedAt();
    entity.acknowledgedBy = incident.acknowledgedBy();
    entity.lastEscalatedAt = incident.lastEscalatedAt();
    entity.escalationCount = incident.escalationCount();
    entity.closedAt = incident.closedAt();
    entity.closedBy = incident.closedBy();
    entity.cause = incident.cause();
    entity.resolutionComment = incident.resolutionComment();
    entity.occurrenceCount = incident.occurrenceCount();
    entity.lastOccurrenceAt = incident.lastOccurrenceAt();
    entity.lastMagnitude = incident.lastMagnitude();
    entity.persistent = incident.persistent();
  }

  static Incident toDomain(IncidentEntity e) {
    return new Incident(
        e.id.toString(),
        e.assetId,
        e.sensorId,
        e.anomalyType,
        e.impact,
        e.urgency,
        e.priority,
        e.status,
        e.createdAt,
        e.ackDueAt,
        e.resolveDueAt,
        e.acknowledgedAt,
        e.acknowledgedBy,
        e.lastEscalatedAt,
        e.escalationCount,
        e.closedAt,
        e.closedBy,
        e.cause,
        e.resolutionComment,
        e.occurrenceCount,
        e.lastOccurrenceAt,
        e.lastMagnitude,
        e.persistent,
        e.sourceReadingId == null ? null : e.sourceReadingId.toString(),
        e.version);
  }
}
