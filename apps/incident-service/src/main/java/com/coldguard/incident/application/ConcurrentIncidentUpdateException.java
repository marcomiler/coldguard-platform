package com.coldguard.incident.application;

/** Someone else changed the incident between this use case reading it and writing it. */
public class ConcurrentIncidentUpdateException extends RuntimeException {

  public ConcurrentIncidentUpdateException(String incidentId, Throwable cause) {
    super("Incident was modified concurrently: " + incidentId, cause);
  }
}
