package com.coldguard.incident.application;

import com.coldguard.commons.messaging.EventActor;
import com.coldguard.commons.messaging.outbox.DomainEventPublisher;
import com.coldguard.commons.messaging.outbox.OutboundEvent;
import com.coldguard.commons.security.Role;
import com.coldguard.incident.application.NotificationRecipients.Recipient;
import com.coldguard.incident.domain.Incident;
import com.coldguard.incident.domain.IncidentEvent;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Turns domain events into outbound events (payloads follow contracts/events/incident) and hands
 * them to the outbox in the caller's transaction.
 */
@Component
class IncidentEventPublisher {

  private static final String AGGREGATE = "Incident";

  private final DomainEventPublisher publisher;
  private final NotificationRecipients recipients;
  private final IncidentAuditTrail audit;
  private final IncidentObserver observer;

  IncidentEventPublisher(
      DomainEventPublisher publisher,
      NotificationRecipients recipients,
      IncidentAuditTrail audit,
      IncidentObserver observer) {
    this.publisher = publisher;
    this.recipients = recipients;
    this.audit = audit;
    this.observer = observer;
  }

  /** Audits the transition and, when it is a catalogued event, emits it through the outbox. */
  void publish(IncidentEvent event, EventActor actor) {
    audit.record(event, actor);
    observer.transitioned(event);
    switch (event) {
      case IncidentEvent.Created created -> {
        Incident i = created.incident();
        send("IncidentCreated", "incident.incident-created", i, i.createdAt(), actor, created(i));
        notify("INCIDENT_CREATED", Role.OPERATIONS_SUPERVISOR, i, i.createdAt(), actor);
      }
      case IncidentEvent.Acknowledged acknowledged -> {
        Incident i = acknowledged.incident();
        send(
            "IncidentAcknowledged",
            "incident.incident-acknowledged",
            i,
            i.acknowledgedAt(),
            actor,
            new Acknowledged(i.id(), i.acknowledgedAt()));
      }
      case IncidentEvent.Escalated escalated -> {
        Incident i = escalated.incident();
        send(
            "IncidentEscalated",
            "incident.incident-escalated",
            i,
            i.lastEscalatedAt(),
            actor,
            new Escalated(
                i.id(),
                i.lastEscalatedAt(),
                escalated.reason(),
                i.priority().name(),
                i.escalationCount()));
        notify("INCIDENT_ESCALATED", Role.MAINTENANCE_TECHNICIAN, i, i.lastEscalatedAt(), actor);
      }
      case IncidentEvent.Closed closed -> {
        Incident i = closed.incident();
        send(
            "IncidentClosed",
            "incident.incident-closed",
            i,
            i.closedAt(),
            actor,
            new Closed(i.id(), i.closedAt(), i.cause(), i.resolutionComment()));
      }
      case IncidentEvent.OccurrenceRegistered ignored -> {
        // Not a catalogued event: the update is visible through the incident itself.
      }
    }
  }

  private void notify(String type, Role role, Incident incident, Instant at, EventActor actor) {
    List<Recipient> to = recipients.enabledWithRole(role);
    if (to.isEmpty()) {
      return;
    }
    send(
        "NotificationRequested",
        "incident.notification-requested",
        incident,
        at,
        actor,
        new NotificationRequested(
            UUID.randomUUID(),
            incident.id(),
            type,
            incident.priority().name(),
            incident.assetId(),
            incident.sensorId(),
            to));
  }

  private void send(
      String type,
      String routingKey,
      Incident incident,
      Instant at,
      EventActor actor,
      Object payload) {
    publisher.publish(
        new OutboundEvent(type, 1, AGGREGATE, incident.id(), routingKey, actor, at, payload));
  }

  private static Created created(Incident i) {
    return new Created(
        i.id(),
        i.assetId(),
        i.sensorId(),
        i.anomalyType(),
        i.impact().name(),
        i.urgency().name(),
        i.priority().name(),
        i.createdAt(),
        i.ackDueAt(),
        i.resolveDueAt());
  }

  record Created(
      String incidentId,
      String assetId,
      String sensorId,
      String anomalyType,
      String impact,
      String urgency,
      String priority,
      Instant createdAt,
      Instant ackDueAt,
      Instant resolveDueAt) {}

  record Acknowledged(String incidentId, Instant acknowledgedAt) {}

  record Escalated(
      String incidentId,
      Instant escalatedAt,
      String reason,
      String priority,
      int escalationCount) {}

  record Closed(String incidentId, Instant closedAt, String cause, String resolutionComment) {}

  record NotificationRequested(
      UUID notificationRequestId,
      String incidentId,
      String notificationType,
      String priority,
      String assetId,
      String sensorId,
      List<Recipient> recipients) {}
}
