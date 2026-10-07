package com.coldguard.incident.auditlog.application;

import com.coldguard.commons.security.Actor;

/** Only auditors may read the audit trail. */
public class AuditAccessDeniedException extends RuntimeException {

  public AuditAccessDeniedException(Actor actor) {
    super(
        actor == null
            ? "No actor identity: role AUDITOR is required"
            : "Actor " + actor.id() + " lacks required role AUDITOR");
  }
}
