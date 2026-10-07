package com.coldguard.incident.domain;

public enum IncidentStatus {
  CREATED,
  ACKNOWLEDGED,
  ESCALATED,
  CLOSED;

  /** An incident is open in every state but {@link #CLOSED}. */
  public boolean isOpen() {
    return this != CLOSED;
  }
}
