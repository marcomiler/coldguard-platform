package com.coldguard.incident.infrastructure;

import com.coldguard.incident.domain.Impact;
import com.coldguard.incident.domain.IncidentStatus;
import com.coldguard.incident.domain.Magnitude;
import com.coldguard.incident.domain.Priority;
import com.coldguard.incident.domain.Urgency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Persistence shape of an incident; the domain model never sees it. See the mapper. */
@Entity
@Table(name = "incident", schema = "incident")
class IncidentEntity {

  @Id UUID id;

  @Column(name = "asset_id", nullable = false)
  String assetId;

  @Column(name = "sensor_id", nullable = false)
  String sensorId;

  @Column(name = "anomaly_type", nullable = false)
  String anomalyType;

  @Enumerated(EnumType.STRING)
  @Column(name = "impact", nullable = false)
  Impact impact;

  @Enumerated(EnumType.STRING)
  @Column(name = "urgency", nullable = false)
  Urgency urgency;

  @Enumerated(EnumType.STRING)
  @Column(name = "priority", nullable = false)
  Priority priority;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  IncidentStatus status;

  @Column(name = "created_at", nullable = false)
  Instant createdAt;

  @Column(name = "ack_due_at")
  Instant ackDueAt;

  @Column(name = "resolve_due_at")
  Instant resolveDueAt;

  @Column(name = "acknowledged_at")
  Instant acknowledgedAt;

  @Column(name = "acknowledged_by")
  String acknowledgedBy;

  @Column(name = "last_escalated_at")
  Instant lastEscalatedAt;

  @Column(name = "escalation_count", nullable = false)
  int escalationCount;

  @Column(name = "closed_at")
  Instant closedAt;

  @Column(name = "closed_by")
  String closedBy;

  @Column(name = "cause")
  String cause;

  @Column(name = "resolution_comment")
  String resolutionComment;

  @Column(name = "occurrence_count", nullable = false)
  int occurrenceCount;

  @Column(name = "last_occurrence_at")
  Instant lastOccurrenceAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "last_magnitude")
  Magnitude lastMagnitude;

  @Column(name = "persistent", nullable = false)
  boolean persistent;

  @Column(name = "source_reading_id")
  UUID sourceReadingId;

  /** Null until first persisted; Hibernate increments it on every update. */
  @Version Long version;

  protected IncidentEntity() {
    // required by JPA
  }
}
