package com.coldguard.incident.application;

import com.coldguard.incident.identity.domain.Actor;

/** Thrown when a close is requested by an actor without the authority required by RN-019. */
public class IncidentCloseForbiddenException extends RuntimeException {

  public IncidentCloseForbiddenException(Actor actor) {
    super(
        actor == null
            ? "No actor identity: not authorized to close an incident"
            : "Actor not authorized to close an incident: " + actor.id());
  }
}
