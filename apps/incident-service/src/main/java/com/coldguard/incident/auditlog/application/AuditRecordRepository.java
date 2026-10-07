package com.coldguard.incident.auditlog.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Append and read only: there is deliberately no update or delete. */
public interface AuditRecordRepository {

  /** Position after which the next page starts (records are newest first). */
  record Cursor(Instant occurredAt, UUID id) {}

  record Filter(
      String entityType,
      String entityId,
      String actorId,
      String action,
      Instant from,
      Instant to) {}

  /**
   * @return false when the entry's source event was already recorded
   */
  boolean append(AuditRecord record, Instant recordedAt, UUID sourceEventId);

  /** Newest first, ordered by (occurredAt, id); {@code from} inclusive, {@code to} exclusive. */
  List<AuditRecord> search(Filter filter, Cursor after, int limit);
}
