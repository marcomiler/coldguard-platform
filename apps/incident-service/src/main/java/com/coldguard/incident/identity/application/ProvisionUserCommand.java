package com.coldguard.incident.identity.application;

import com.coldguard.commons.security.Role;
import java.util.Set;

/** {@code password} is sensitive: never log or print this command. */
public record ProvisionUserCommand(
    String username,
    String email,
    String displayName,
    String password,
    Set<Role> roles,
    String actorId) {

  @Override
  public String toString() {
    return "ProvisionUserCommand[username=%s, roles=%s]".formatted(username, roles);
  }
}
