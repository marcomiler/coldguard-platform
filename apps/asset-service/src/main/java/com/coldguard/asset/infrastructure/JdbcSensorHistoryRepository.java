package com.coldguard.asset.infrastructure;

import com.coldguard.asset.application.AssignmentEntry;
import com.coldguard.asset.application.SensorHistoryEntry;
import com.coldguard.asset.application.SensorHistoryRepository;
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
}
