package com.coldguard.asset.infrastructure;

import com.coldguard.asset.application.AssignmentEntry;
import com.coldguard.asset.application.HistoryCursor;
import com.coldguard.asset.application.SensorHistoryEntry;
import com.coldguard.asset.application.SensorHistoryRepository;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.ObjectMapper;

/** Insert-only: the sensor's history is never changed or deleted. */
@Repository
class JdbcSensorHistoryRepository implements SensorHistoryRepository {

  private final JdbcClient jdbc;
  private final ObjectMapper mapper;

  JdbcSensorHistoryRepository(JdbcClient jdbc, ObjectMapper mapper) {
    this.jdbc = jdbc;
    this.mapper = mapper;
  }

  @Override
  public void addAssignment(AssignmentEntry entry) {
    jdbc.sql(
            """
            INSERT INTO sensor_assignment_history
              (id, sensor_id, asset_id, previous_asset_id, assigned_at, assigned_by, reason)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """)
        .params(
            entry.id(),
            entry.sensorId(),
            entry.assetId(),
            entry.previousAssetId(),
            JdbcSupport.timestamp(entry.assignedAt()),
            entry.assignedBy(),
            entry.reason())
        .update();
  }

  @Override
  public void addEntry(SensorHistoryEntry entry) {
    jdbc.sql(
            """
            INSERT INTO sensor_lifecycle_audit
              (id, sensor_id, action, previous_value, new_value, reason, actor_type, actor_id,
               occurred_at)
            VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?)
            """)
        .params(
            entry.id(),
            entry.sensorId(),
            entry.action(),
            json(entry.previousValue()),
            json(entry.newValue()),
            entry.reason(),
            entry.actorType(),
            entry.actorId(),
            JdbcSupport.timestamp(entry.occurredAt()))
        .update();
  }

  private String json(Object value) {
    return value == null ? null : mapper.writeValueAsString(value);
  }

  @Override
  public List<SensorHistoryEntry> findPage(UUID sensorId, HistoryCursor cursor, int limit) {
    Timestamp at = cursor == null ? null : JdbcSupport.timestamp(cursor.occurredAt());
    UUID id = cursor == null ? null : cursor.id();
    return jdbc.sql(
            """
            SELECT id, sensor_id, action, previous_value::text AS previous_value,
                   new_value::text AS new_value, reason, actor_type, actor_id, occurred_at
              FROM sensor_lifecycle_audit
             WHERE sensor_id = ?
               AND (?::timestamptz IS NULL OR (occurred_at, id) < (?::timestamptz, ?::uuid))
             ORDER BY occurred_at DESC, id DESC
             LIMIT ?
            """)
        .params(sensorId, at, at, id, limit)
        .query(
            (rs, row) ->
                new SensorHistoryEntry(
                    rs.getObject("id", UUID.class),
                    rs.getObject("sensor_id", UUID.class),
                    rs.getString("action"),
                    map(rs.getString("previous_value")),
                    map(rs.getString("new_value")),
                    rs.getString("reason"),
                    rs.getString("actor_type"),
                    rs.getString("actor_id"),
                    JdbcSupport.instant(rs, "occurred_at")))
        .list();
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> map(String json) {
    return json == null ? null : mapper.readValue(json, Map.class);
  }
}
