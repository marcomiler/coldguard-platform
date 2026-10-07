package com.coldguard.incident.auditlog.application;

import com.coldguard.commons.correlation.CorrelationContext;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
class AuditLogRecorder implements AuditRecorder {

  private final AuditRecordRepository repository;
  private final Clock clock;

  AuditLogRecorder(AuditRecordRepository repository, Clock clock) {
    this.repository = repository;
    this.clock = clock;
  }

  @Override
  public void record(AuditEntry entry) {
    String correlationId =
        entry.correlationId() != null
            ? entry.correlationId()
            : CorrelationContext.current().orElse(null);
    AuditRecord record =
        new AuditRecord(
            UUID.randomUUID(),
            entry.occurredAt(),
            entry.sourceService(),
            entry.entityType(),
            entry.entityId(),
            entry.action(),
            entry.actorType().name(),
            entry.actorId(),
            entry.reason(),
            AuditJson.normalise(entry.before()),
            AuditJson.normalise(entry.after()),
            correlationId);
    repository.append(record, clock.instant(), entry.sourceEventId());
  }
}
