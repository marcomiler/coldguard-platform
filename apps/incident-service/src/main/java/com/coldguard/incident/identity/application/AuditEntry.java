package com.coldguard.incident.identity.application;

import java.time.Instant;

/** What Identity asks the audit log to keep. Never carries passwords or hashes. */
public record AuditEntry(
    String action,
    String entityType,
    String entityId,
    String actorId,
    String reason,
    String before,
    String after,
    Instant occurredAt) {}
