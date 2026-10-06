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
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcSensorConditionRepository implements SensorConditionRepository {

  private static final String SELECT =
      """
      SELECT sensor_id, asset_id, sensor_status, expected_interval_seconds, last_reading_at,
             last_evaluated_recorded_at, breach_anomaly_type, breach_streak,
             breach_streak_started_at, connectivity_lost_at, asset_state_at, version
        FROM sensor_condition
      """;

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
    return lockExisting(context.sensorId()).orElseThrow();
  }

  @Override
  public Optional<SensorCondition> lockExisting(UUID sensorId) {
    return jdbc.sql(SELECT + " WHERE sensor_id = ? FOR UPDATE")
        .param(sensorId)
        .query(this::map)
        .optional();
  }

  @Override
  public List<SensorCondition> lockOverdue(Instant now, double toleranceFactor, int limit) {
    return jdbc.sql(
            SELECT
                + """
                 WHERE connectivity_lost_at IS NULL
                   AND sensor_status = 'ACTIVE'
                   AND expected_interval_seconds > 0
                   AND last_reading_at + (expected_interval_seconds * ?) * interval '1 second' < ?
                 ORDER BY last_reading_at
                 LIMIT ?
                   FOR UPDATE SKIP LOCKED
                """)
        .params(toleranceFactor, Timestamp.from(now), limit)
        .query(this::map)
        .list();
  }

  @Override
  public List<SensorCondition> findPage(boolean onlyLost, int page, int size) {
    return jdbc.sql(
            SELECT
                + " WHERE (? = false OR connectivity_lost_at IS NOT NULL)"
                + " ORDER BY sensor_id LIMIT ? OFFSET ?")
        .params(onlyLost, size, (long) page * size)
        .query(this::map)
        .list();
  }

  @Override
  public long count(boolean onlyLost) {
    return jdbc.sql(
            "SELECT count(*) FROM sensor_condition WHERE (? = false OR connectivity_lost_at IS NOT NULL)")
        .param(onlyLost)
        .query(Long.class)
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
                       asset_state_at = ?, version = version + 1
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
                timestamp(condition.assetStateAt()),
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
        instant(rs, "asset_state_at"),
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
