package com.coldguard.incident.domain;

public class IncidentAlreadyClosedException extends RuntimeException {

  public IncidentAlreadyClosedException(String incidentId) {
    super("Incident is already closed: " + incidentId);
  }
}
