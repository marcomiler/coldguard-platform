package com.coldguard.asset.application;

import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.Role;

/**
 * Defense in depth: the Gateway already filters by role, but each use case checks it again from the
 * actor the Gateway propagated. Commands are for administrators; queries also admit supervisors.
 */
final class AccessPolicy {

  private AccessPolicy() {}

  static void requireAdministrator(Actor actor) {
    if (actor == null || !actor.hasRole(Role.PLATFORM_ADMIN)) {
      throw new AssetAccessDeniedException("PLATFORM_ADMIN role required");
    }
  }

  static void requireReader(Actor actor) {
    if (actor == null
        || !(actor.hasRole(Role.PLATFORM_ADMIN) || actor.hasRole(Role.OPERATIONS_SUPERVISOR))) {
      throw new AssetAccessDeniedException("PLATFORM_ADMIN or OPERATIONS_SUPERVISOR role required");
    }
  }
}
