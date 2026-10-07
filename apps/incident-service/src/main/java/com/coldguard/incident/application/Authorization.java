package com.coldguard.incident.application;

import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.Role;

final class Authorization {

  private Authorization() {}

  static Actor require(Actor actor, Role role) {
    if (actor == null || !actor.hasRole(role)) {
      throw new ActorNotAuthorizedException(actor, role);
    }
    return actor;
  }
}
