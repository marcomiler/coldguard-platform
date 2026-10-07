package com.coldguard.incident.domain;

import java.time.Clock;
import java.time.Instant;

/**
 * An incident and its lifecycle. Every transition returns a new state plus the event describing it;
 * nothing is mutated. {@code version} is the persistence version (null until first stored) used for
 * optimistic locking.
 */
public record Incident(
    String id,
    String assetId,
    String sensorId,
    String anomalyType,
    Impact impact,
    Urgency urgency,
    Priority priority,
    IncidentStatus status,
    Instant createdAt,
    Instant ackDueAt,
    Instant resolveDueAt,
    Instant acknowledgedAt,
    String acknowledgedBy,
    Instant lastEscalatedAt,
    int escalationCount,
    Instant closedAt,
    String closedBy,
    String cause,
    String resolutionComment,
    int occurrenceCount,
    Instant lastOccurrenceAt,
    Magnitude lastMagnitude,
    boolean persistent,
    String sourceReadingId,
    Long version) {

  public static final int MAX_ESCALATION_REASON_LENGTH = 500;

  /** Opens a new incident; the SLA clock starts now. */
  public static Transition open(
      String id,
      String assetId,
      String sensorId,
      String anomalyType,
      Criticality assetCriticality,
      Magnitude magnitude,
      boolean persistent,
      String sourceReadingId,
      SlaPolicy slaPolicy,
      Clock clock) {
    Instant now = clock.instant();
    Impact impact = PriorityPolicy.impactFrom(assetCriticality);
    Urgency urgency = PriorityPolicy.urgencyFrom(magnitude, persistent);
    Priority priority = PriorityPolicy.priorityFrom(impact, urgency);
    Incident incident =
        new Incident(
            id,
            assetId,
            sensorId,
            anomalyType,
            impact,
            urgency,
            priority,
            IncidentStatus.CREATED,
            now,
            slaPolicy.ackDueAt(priority, now),
            slaPolicy.resolveDueAt(priority, now).orElse(null),
            null,
            null,
            null,
            0,
            null,
            null,
            null,
            null,
            1,
            now,
            magnitude,
            persistent,
            sourceReadingId,
            null);
    return new Transition(incident, new IncidentEvent.Created(incident));
  }

  /**
   * Stops the acknowledgement clock. An escalated incident stays escalated.
   *
   * @throws IncidentAlreadyClosedException if closed
   * @throws IncidentAlreadyAcknowledgedException if already acknowledged
   */
  public Transition acknowledge(String actorId, Clock clock) {
    requireOpen();
    if (acknowledgedAt != null) {
      throw new IncidentAlreadyAcknowledgedException(id);
    }
    IncidentStatus next = status == IncidentStatus.CREATED ? IncidentStatus.ACKNOWLEDGED : status;
    Incident acknowledged =
        new Incident(
            id,
            assetId,
            sensorId,
            anomalyType,
            impact,
            urgency,
            priority,
            next,
            createdAt,
            ackDueAt,
            resolveDueAt,
            clock.instant(),
            actorId,
            lastEscalatedAt,
            escalationCount,
            closedAt,
            closedBy,
            cause,
            resolutionComment,
            occurrenceCount,
            lastOccurrenceAt,
            lastMagnitude,
            persistent,
            sourceReadingId,
            version);
    return new Transition(acknowledged, new IncidentEvent.Acknowledged(acknowledged));
  }

  /**
   * Escalates from any open state; repeatable.
   *
   * @throws IncidentAlreadyClosedException if closed
   * @throws IllegalArgumentException if the reason is blank or too long
   */
  public Transition escalate(String reason, Clock clock) {
    requireOpen();
    String checkedReason =
        CloseEvidence.requireBounded(reason, "reason", MAX_ESCALATION_REASON_LENGTH);
    Incident escalated =
        new Incident(
            id,
            assetId,
            sensorId,
            anomalyType,
            impact,
            urgency,
            priority,
            IncidentStatus.ESCALATED,
            createdAt,
            ackDueAt,
            resolveDueAt,
            acknowledgedAt,
            acknowledgedBy,
            clock.instant(),
            escalationCount + 1,
            closedAt,
            closedBy,
            cause,
            resolutionComment,
            occurrenceCount,
            lastOccurrenceAt,
            lastMagnitude,
            persistent,
            sourceReadingId,
            version);
    return new Transition(escalated, new IncidentEvent.Escalated(escalated, checkedReason));
  }

  /**
   * Closes from any open state. Closing without a prior acknowledgement is allowed; such an
   * incident is simply left out of the mean-time-to-acknowledge.
   *
   * @throws IncidentAlreadyClosedException if already closed
   */
  public Transition close(String actorId, CloseEvidence evidence, Clock clock) {
    requireOpen();
    Incident closed =
        new Incident(
            id,
            assetId,
            sensorId,
            anomalyType,
            impact,
            urgency,
            priority,
            IncidentStatus.CLOSED,
            createdAt,
            ackDueAt,
            resolveDueAt,
            acknowledgedAt,
            acknowledgedBy,
            lastEscalatedAt,
            escalationCount,
            clock.instant(),
            actorId,
            evidence.cause(),
            evidence.resolutionComment(),
            occurrenceCount,
            lastOccurrenceAt,
            lastMagnitude,
            persistent,
            sourceReadingId,
            version);
    return new Transition(closed, new IncidentEvent.Closed(closed));
  }

  /**
   * Registers another equivalent anomaly. Urgency never goes down; when it rises and changes the
   * priority, both SLA deadlines are recomputed from the creation time.
   *
   * @throws IncidentAlreadyClosedException if closed
   */
  public Transition registerOccurrence(
      Magnitude magnitude, boolean persistentNow, SlaPolicy slaPolicy, Clock clock) {
    requireOpen();
    Urgency observed = PriorityPolicy.urgencyFrom(magnitude, persistentNow);
    Urgency nextUrgency = observed.ordinal() > urgency.ordinal() ? observed : urgency;
    Priority nextPriority = PriorityPolicy.priorityFrom(impact, nextUrgency);
    boolean priorityChanged = nextPriority != priority;
    Incident updated =
        new Incident(
            id,
            assetId,
            sensorId,
            anomalyType,
            impact,
            nextUrgency,
            nextPriority,
            status,
            createdAt,
            priorityChanged ? slaPolicy.ackDueAt(nextPriority, createdAt) : ackDueAt,
            priorityChanged
                ? slaPolicy.resolveDueAt(nextPriority, createdAt).orElse(null)
                : resolveDueAt,
            acknowledgedAt,
            acknowledgedBy,
            lastEscalatedAt,
            escalationCount,
            closedAt,
            closedBy,
            cause,
            resolutionComment,
            occurrenceCount + 1,
            clock.instant(),
            magnitude,
            persistent || persistentNow,
            sourceReadingId,
            version);
    return new Transition(updated, new IncidentEvent.OccurrenceRegistered(updated, priority));
  }

  private void requireOpen() {
    if (!status.isOpen()) {
      throw new IncidentAlreadyClosedException(id);
    }
  }
}
