package com.coldguard.incident.application;

import com.coldguard.commons.messaging.EventActor;
import com.coldguard.incident.auditlog.application.AuditEntry;
import com.coldguard.incident.auditlog.application.AuditEntry.ActorType;
import com.coldguard.incident.auditlog.application.AuditJson;
import com.coldguard.incident.auditlog.application.AuditRecorder;
import com.coldguard.incident.domain.Incident;
import com.coldguard.incident.domain.IncidentEvent;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Records every relevant incident transition in the audit log, in the use case transaction. */
@Component
class IncidentAuditTrail {

  private static final String ENTITY = "Incident";
  private static final String SOURCE = "incident-service";

  private final AuditRecorder recorder;

  IncidentAuditTrail(AuditRecorder recorder) {
    this.recorder = recorder;
  }

  void record(IncidentEvent event, EventActor actor) {
    Incident i = event.incident();
    switch (event) {
      case IncidentEvent.Created ignored ->
          write(
              i,
              actor,
              "CREATED",
              i.createdAt(),
              null,
              null,
              AuditJson.object(
                  "status", i.status(),
                  "impact", i.impact(),
                  "urgency", i.urgency(),
                  "priority", i.priority(),
                  "ackDueAt", i.ackDueAt(),
                  "resolveDueAt", i.resolveDueAt()));
      case IncidentEvent.Acknowledged ignored ->
          write(
              i,
              actor,
              "ACKNOWLEDGED",
              i.acknowledgedAt(),
              null,
              null,
              AuditJson.object("status", i.status(), "acknowledgedAt", i.acknowledgedAt()));
      case IncidentEvent.Escalated escalated ->
          write(
              i,
              actor,
              "ESCALATED",
              i.lastEscalatedAt(),
              escalated.reason(),
              null,
              AuditJson.object("status", i.status(), "escalationCount", i.escalationCount()));
      case IncidentEvent.Closed ignored ->
          write(
              i,
              actor,
              "CLOSED",
              i.closedAt(),
              i.cause(),
              null,
              AuditJson.object("status", i.status(), "closedAt", i.closedAt()));
      case IncidentEvent.OccurrenceRegistered occurrence -> {
        write(
            i,
            actor,
            "OCCURRENCE_REGISTERED",
            i.lastOccurrenceAt(),
            null,
            null,
            AuditJson.object(
                "occurrenceCount", i.occurrenceCount(), "magnitude", i.lastMagnitude()));
        if (occurrence.priorityRecalculated()) {
          write(
              i,
              actor,
              "PRIORITY_RECALCULATED",
              i.lastOccurrenceAt(),
              "Urgency rose with a new occurrence",
              AuditJson.object("priority", occurrence.previousPriority()),
              AuditJson.object(
                  "priority", i.priority(),
                  "urgency", i.urgency(),
                  "ackDueAt", i.ackDueAt(),
                  "resolveDueAt", i.resolveDueAt()));
        }
      }
    }
  }

  private void write(
      Incident i,
      EventActor actor,
      String action,
      Instant at,
      String reason,
      String before,
      String after) {
    recorder.record(
        new AuditEntry(
            SOURCE,
            ENTITY,
            i.id(),
            action,
            actor.type() == EventActor.Type.SYSTEM ? ActorType.SYSTEM : ActorType.USER,
            actor.id(),
            reason,
            before,
            after,
            at,
            null,
            null));
  }
}
