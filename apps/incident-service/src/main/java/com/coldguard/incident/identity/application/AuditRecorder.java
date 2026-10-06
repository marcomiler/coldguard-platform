package com.coldguard.incident.identity.application;

/**
 * Port to the Audit Log module. Called inside the use case transaction so the change and its audit
 * record commit together.
 */
public interface AuditRecorder {

  void record(AuditEntry entry);
}
