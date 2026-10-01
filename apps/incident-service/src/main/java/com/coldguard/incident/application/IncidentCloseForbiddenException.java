package com.coldguard.incident.application;

/** Thrown when a close is requested by an actor without the authority required by RN-019. */
public class IncidentCloseForbiddenException extends RuntimeException {

  public IncidentCloseForbiddenException(String actorRole) {
    super("Actor role not authorized to close an incident: " + actorRole);
  }
}
