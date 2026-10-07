package com.coldguard.incident.auditlog.infrastructure;

import com.coldguard.incident.auditlog.application.AuditRecord;
import com.coldguard.incident.auditlog.application.AuditRecordRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAuditRecordRepository implements AuditRecordRepository {

  private static final String INSERT =
      """
      INSERT INTO auditlog.audit_record
        (id, occurred_at, recorded_at, source_service, entity_type, entity_id, action,
         actor_type, actor_id, reason, previous_value, new_value, correlation_id, source_event_id)
      VALUES (:id, :occurredAt, :recordedAt, :sourceService, :entityType, :entityId, :action,
              :actorType, :actorId, :reason, CAST(:previous AS jsonb), CAST(:new AS jsonb), :correlationId,
              :sourceEventId)
      ON CONFLICT (source_event_id) WHERE source_event_id IS NOT NULL DO NOTHING
      """;

  private final JdbcClient jdbc;

  JdbcAuditRecordRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public boolean append(AuditRecord record, Instant recordedAt, UUID sourceEventId) {
    return jdbc.sql(INSERT)
            .param("id", record.id())
            .param("occurredAt", Timestamp.from(record.occurredAt()))
            .param("recordedAt", Timestamp.from(recordedAt))
            .param("sourceService", record.sourceService())
            .param("entityType", record.entityType())
            .param("entityId", record.entityId())
            .param("action", record.action())
            .param("actorType", record.actorType())
            .param("actorId", record.actorId())
            .param("reason", record.reason())
            .param("previous", record.previousValue())
            .param("new", record.newValue())
            .param("correlationId", record.correlationId())
            .param("sourceEventId", sourceEventId)
            .update()
        == 1;
  }

  @Override
  public List<AuditRecord> search(Filter filter, Cursor after, int limit) {
    StringBuilder sql =
        new StringBuilder(
            """
            SELECT id, occurred_at, source_service, entity_type, entity_id, action, actor_type,
                   actor_id, reason, previous_value::text AS previous_value,
                   new_value::text AS new_value, correlation_id
              FROM auditlog.audit_record
             WHERE occurred_at >= :from AND occurred_at < :to
            """);
    java.util.Map<String, Object> params = new java.util.LinkedHashMap<>();
    params.put("from", Timestamp.from(filter.from()));
    params.put("to", Timestamp.from(filter.to()));
    if (filter.entityType() != null) {
      sql.append(" AND entity_type = :entityType");
      params.put("entityType", filter.entityType());
    }
    if (filter.entityId() != null) {
      sql.append(" AND entity_id = :entityId");
      params.put("entityId", filter.entityId());
    }
    if (filter.actorId() != null) {
      sql.append(" AND actor_id = :actorId");
      params.put("actorId", filter.actorId());
    }
    if (filter.action() != null) {
      sql.append(" AND action = :action");
      params.put("action", filter.action());
    }
    if (after != null) {
      sql.append(" AND (occurred_at, id) < (:afterAt, :afterId)");
      params.put("afterAt", Timestamp.from(after.occurredAt()));
      params.put("afterId", after.id());
    }
    sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT :limit");
    params.put("limit", limit);
    return jdbc.sql(sql.toString())
        .params(params)
        .query(
            (rs, n) ->
                new AuditRecord(
                    rs.getObject("id", UUID.class),
                    rs.getTimestamp("occurred_at").toInstant(),
                    rs.getString("source_service"),
                    rs.getString("entity_type"),
                    rs.getString("entity_id"),
                    rs.getString("action"),
                    rs.getString("actor_type"),
                    rs.getString("actor_id"),
                    rs.getString("reason"),
                    rs.getString("previous_value"),
                    rs.getString("new_value"),
                    rs.getString("correlation_id")))
        .list();
  }
}
