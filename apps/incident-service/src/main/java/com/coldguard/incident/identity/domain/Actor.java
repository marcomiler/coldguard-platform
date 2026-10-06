package com.coldguard.incident.identity.domain;

import java.util.Objects;
import java.util.Set;

/** The identity on whose behalf a use case runs: a user with roles, or a named system process. */
public record Actor(String id, Set<Role> roles) {

  public Actor {
    Objects.requireNonNull(id, "id");
    roles = Set.copyOf(roles);
  }

  public static Actor system(String process) {
    return new Actor("system:" + process, Set.of());
  }

  public boolean hasRole(Role role) {
    return roles.contains(role);
  }
}
