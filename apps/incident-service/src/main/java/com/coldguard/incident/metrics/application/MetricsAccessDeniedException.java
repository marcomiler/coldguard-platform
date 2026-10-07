package com.coldguard.incident.metrics.application;

import com.coldguard.commons.security.Actor;

public class MetricsAccessDeniedException extends RuntimeException {

  public MetricsAccessDeniedException(Actor actor) {
    super(
        actor == null
            ? "No actor identity: role OPERATIONS_SUPERVISOR is required"
            : "Actor " + actor.id() + " lacks required role OPERATIONS_SUPERVISOR");
  }
}
