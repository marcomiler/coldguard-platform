package com.coldguard.telemetry.infrastructure;

import com.coldguard.telemetry.application.SensorConditionRepository;
import com.coldguard.telemetry.domain.AnomalyType;
import com.coldguard.telemetry.domain.SensorCondition;
import com.coldguard.telemetry.domain.SensorContext;
import com.coldguard.telemetry.domain.SensorStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSensorConditionRepository implements SensorConditionRepository {

  private final JdbcClient jdbc;

  JdbcSensorConditionRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public SensorCondition lockOrCreate(SensorContext context, Instant receivedAt) {
    SensorCondition initial = SensorCondition.first(context, receivedAt);
    // A concurrent first batch for the same sensor makes this wait for its commit, then do nothing.
    jdbc.sql(
            """
            INSERT INTO sensor_condition
              (sensor_id, asset_id, sensor_status, expected_interval_seconds, last_reading_at,
               breach_streak, version)
            VALUES (?, ?, ?, ?, ?, 0, 1)
            ON CONFLICT (sensor_id) DO NOTHING
            """)
        .params(
            initial.sensorId(),
            initial.assetId(),
            initial.sensorStatus().name(),
            initial.expectedIntervalSeconds(),
            Timestamp.from(receivedAt))
        .update();
    return jdbc.sql(
            """
            SELECT sensor_id, asset_id, sensor_status, expected_interval_seconds, last_reading_at,
                   last_evaluated_recorded_at, breach_anomaly_type, breach_streak,
                   breach_streak_started_at, connectivity_lost_at, version
              FROM sensor_condition
             WHERE sensor_id = ?
               FOR UPDATE
            """)
        .param(context.sensorId())
        .query(this::map)
        .single();
  }

  @Override
  public void save(SensorCondition condition) {
    int updated =
        jdbc.sql(
                """
                UPDATE sensor_condition
                   SET asset_id = ?, sensor_status = ?, expected_interval_seconds = ?,
                       last_reading_at = ?, last_evaluated_recorded_at = ?, breach_anomaly_type = ?,
                       breach_streak = ?, breach_streak_started_at = ?, connectivity_lost_at = ?,
                       version = version + 1
                 WHERE sensor_id = ? AND version = ?
                """)
            .params(
                condition.assetId(),
                condition.sensorStatus().name(),
                condition.expectedIntervalSeconds(),
                timestamp(condition.lastReadingAt()),
                timestamp(condition.lastEvaluatedRecordedAt()),
                condition.breachAnomalyType() == null ? null : condition.breachAnomalyType().name(),
                condition.breachStreak(),
                timestamp(condition.breachStreakStartedAt()),
                timestamp(condition.connectivityLostAt()),
                condition.sensorId(),
                condition.version())
            .update();
    if (updated == 0) {
      throw new IllegalStateException(
          "The condition of sensor " + condition.sensorId() + " changed while it was locked");
    }
  }

  private SensorCondition map(ResultSet rs, int row) throws SQLException {
    String anomaly = rs.getString("breach_anomaly_type");
    return new SensorCondition(
        rs.getObject("sensor_id", UUID.class),
        rs.getObject("asset_id", UUID.class),
        SensorStatus.valueOf(rs.getString("sensor_status")),
        rs.getInt("expected_interval_seconds"),
        instant(rs, "last_reading_at"),
        instant(rs, "last_evaluated_recorded_at"),
        anomaly == null ? null : AnomalyType.valueOf(anomaly),
        rs.getInt("breach_streak"),
        instant(rs, "breach_streak_started_at"),
        instant(rs, "connectivity_lost_at"),
        rs.getLong("version"));
  }

  private static Timestamp timestamp(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }

  private static Instant instant(ResultSet rs, String column) throws SQLException {
    OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
    return value == null ? null : value.toInstant();
  }
}
