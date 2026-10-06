package com.coldguard.telemetry.infrastructure;

import com.coldguard.telemetry.application.ReadingCursor;
import com.coldguard.telemetry.application.ReadingRepository;
import com.coldguard.telemetry.application.StoredReading;
import com.coldguard.telemetry.domain.AnomalyType;
import com.coldguard.telemetry.domain.IneligibilityReason;
import com.coldguard.telemetry.domain.MagnitudeLevel;
import com.coldguard.telemetry.domain.ReadingSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Readings are evidence: this repository only inserts and reads, never updates or deletes. */
@Repository
class JdbcReadingRepository implements ReadingRepository {

  private static final String INSERT =
      """
      INSERT INTO telemetry_reading
        (id, sensor_id, asset_id, recorded_at, received_at, value, unit, source, eligible,
         ineligibility_reason, breached, anomaly_type, magnitude, correlation_id)
      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
      ON CONFLICT (id) DO NOTHING
      """;

  private final JdbcClient jdbc;
  private final org.springframework.jdbc.core.JdbcTemplate template;

  JdbcReadingRepository(JdbcClient jdbc, org.springframework.jdbc.core.JdbcTemplate template) {
    this.jdbc = jdbc;
    this.template = template;
  }

  @Override
  public Set<UUID> findExisting(Collection<UUID> ids) {
    if (ids.isEmpty()) {
      return Set.of();
    }
    return new HashSet<>(
        jdbc.sql("SELECT id FROM telemetry_reading WHERE id = ANY (?)")
            .param(ids.toArray(UUID[]::new))
            .query(UUID.class)
            .list());
  }

  @Override
  public void insertAll(List<StoredReading> readings) {
    if (readings.isEmpty()) {
      return;
    }
    template.batchUpdate(
        INSERT,
        readings,
        readings.size(),
        (statement, reading) -> {
          statement.setObject(1, reading.id());
          statement.setObject(2, reading.sensorId());
          statement.setObject(3, reading.assetId());
          statement.setTimestamp(4, Timestamp.from(reading.recordedAt()));
          statement.setTimestamp(5, Timestamp.from(reading.receivedAt()));
          statement.setBigDecimal(6, reading.value());
          statement.setString(7, reading.unit());
          statement.setString(8, reading.source().name());
          statement.setBoolean(9, reading.eligible());
          statement.setString(10, name(reading.ineligibilityReason()));
          statement.setBoolean(11, reading.breached());
          statement.setString(12, name(reading.anomalyType()));
          statement.setString(13, name(reading.magnitude()));
          statement.setString(14, reading.correlationId());
        });
  }

  @Override
  public List<StoredReading> findPage(
      UUID sensorId, Instant from, Instant to, ReadingCursor after, int limit) {
    Timestamp afterAt = after == null ? null : Timestamp.from(after.recordedAt());
    UUID afterId = after == null ? null : after.id();
    return jdbc.sql(
            """
            SELECT id, sensor_id, asset_id, recorded_at, received_at, value, unit, source, eligible,
                   ineligibility_reason, breached, anomaly_type, magnitude, correlation_id
              FROM telemetry_reading
             WHERE sensor_id = ?
               AND recorded_at >= ? AND recorded_at < ?
               AND (?::timestamptz IS NULL OR (recorded_at, id) < (?::timestamptz, ?::uuid))
             ORDER BY recorded_at DESC, id DESC
             LIMIT ?
            """)
        .params(
            sensorId, Timestamp.from(from), Timestamp.from(to), afterAt, afterAt, afterId, limit)
        .query(this::map)
        .list();
  }

  private StoredReading map(ResultSet rs, int row) throws SQLException {
    String reason = rs.getString("ineligibility_reason");
    String anomaly = rs.getString("anomaly_type");
    String magnitude = rs.getString("magnitude");
    return new StoredReading(
        rs.getObject("id", UUID.class),
        rs.getObject("sensor_id", UUID.class),
        rs.getObject("asset_id", UUID.class),
        rs.getObject("recorded_at", OffsetDateTime.class).toInstant(),
        rs.getObject("received_at", OffsetDateTime.class).toInstant(),
        rs.getBigDecimal("value"),
        rs.getString("unit"),
        ReadingSource.valueOf(rs.getString("source")),
        rs.getBoolean("eligible"),
        reason == null ? null : IneligibilityReason.valueOf(reason),
        rs.getBoolean("breached"),
        anomaly == null ? null : AnomalyType.valueOf(anomaly),
        magnitude == null ? null : MagnitudeLevel.valueOf(magnitude),
        rs.getString("correlation_id"));
  }

  private static String name(Enum<?> value) {
    return value == null ? null : value.name();
  }
}
