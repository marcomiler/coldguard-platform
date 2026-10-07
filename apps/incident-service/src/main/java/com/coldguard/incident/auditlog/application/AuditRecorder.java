package com.coldguard.incident.auditlog.application;

/**
 * Port to the Audit Log module. In-process callers invoke it inside their use case transaction so
 * the change and its audit record commit together.
 */
public interface AuditRecorder {

  /** Appends the entry; an entry whose {@code sourceEventId} was already recorded is ignored. */
  void record(AuditEntry entry);
}
