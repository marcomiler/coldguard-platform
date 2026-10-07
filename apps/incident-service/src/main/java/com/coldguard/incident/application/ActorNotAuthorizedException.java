package com.coldguard.incident.application;

import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.Role;

/** The caller lacks the role the operation requires, or carried no trusted identity at all. */
public class ActorNotAuthorizedException extends RuntimeException {

  public ActorNotAuthorizedException(Actor actor, Role required) {
    super(
        actor == null
            ? "No actor identity: role " + required + " is required"
            : "Actor " + actor.id() + " lacks required role " + required);
  }
}
