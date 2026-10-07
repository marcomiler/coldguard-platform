package com.coldguard.incident.auditlog.application;

import java.time.Instant;
import java.util.UUID;

/**
 * What a module asks the audit log to keep. {@code before} and {@code after} are a JSON object, or
 * free text that is stored as {@code {"summary": text}}. Never carries passwords, hashes or tokens.
 * {@code sourceEventId} makes a record that comes from an event idempotent; {@code correlationId}
 * defaults to the current request's when null.
 */
public record AuditEntry(
    String sourceService,
    String entityType,
    String entityId,
    String action,
    ActorType actorType,
    String actorId,
    String reason,
    String before,
    String after,
    Instant occurredAt,
    String correlationId,
    UUID sourceEventId) {

  public enum ActorType {
    USER,
    SYSTEM
  }

  /** A change made in-process by a user of Identity & Access. */
  public AuditEntry(
      String action,
      String entityType,
      String entityId,
      String actorId,
      String reason,
      String before,
      String after,
      Instant occurredAt) {
    this(
        "incident-service",
        entityType,
        entityId,
        action,
        ActorType.USER,
        actorId,
        reason,
        before,
        after,
        occurredAt,
        null,
        null);
  }
}
