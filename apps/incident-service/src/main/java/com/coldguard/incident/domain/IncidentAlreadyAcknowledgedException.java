package com.coldguard.incident.domain;

public class IncidentAlreadyAcknowledgedException extends RuntimeException {

  public IncidentAlreadyAcknowledgedException(String incidentId) {
    super("Incident is already acknowledged: " + incidentId);
  }
}
