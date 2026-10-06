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

  /**
   * An internal service calling with its own certificate (for example Telemetry reading evaluation
   * contexts). Such callers are resolved from the certificate, never from request metadata.
   */
  static void requireSystemCaller(Actor actor) {
    if (actor == null || !actor.id().startsWith("system:")) {
      throw new AssetAccessDeniedException("An internal service caller is required");
    }
  }
}
