package com.coldguard.incident.auditlog.application;

import java.time.Instant;
import java.util.UUID;

/** A stored, immutable audit record; the JSON values are documents as text. */
public record AuditRecord(
    UUID id,
    Instant occurredAt,
    String sourceService,
    String entityType,
    String entityId,
    String action,
    String actorType,
    String actorId,
    String reason,
    String previousValue,
    String newValue,
    String correlationId) {}
