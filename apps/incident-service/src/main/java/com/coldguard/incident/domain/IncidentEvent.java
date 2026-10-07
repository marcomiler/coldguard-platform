package com.coldguard.incident.domain;

/** What happened to an incident; carries the resulting snapshot so publishers need no lookup. */
public sealed interface IncidentEvent {

  Incident incident();

  record Created(Incident incident) implements IncidentEvent {}

  record Acknowledged(Incident incident) implements IncidentEvent {}

  record Escalated(Incident incident, String reason) implements IncidentEvent {}

  record Closed(Incident incident) implements IncidentEvent {}

  /** A further equivalent anomaly arrived while the incident was open. */
  record OccurrenceRegistered(Incident incident, Priority previousPriority)
      implements IncidentEvent {

    public boolean priorityRecalculated() {
      return previousPriority != incident.priority();
    }
  }
}
