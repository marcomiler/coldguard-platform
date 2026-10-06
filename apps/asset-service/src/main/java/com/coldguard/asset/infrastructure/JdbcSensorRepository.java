package com.coldguard.asset.infrastructure;

import com.coldguard.asset.application.DueSensor;
import com.coldguard.asset.application.SensorRepository;
import com.coldguard.asset.domain.AlreadyExistsException;
import com.coldguard.asset.domain.Sensor;
import com.coldguard.asset.domain.SensorStatus;
import com.coldguard.asset.domain.StaleVersionException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Sensors are never deleted: this repository has no delete operation by design. */
@Repository
class JdbcSensorRepository implements SensorRepository {

  private static final String COLUMNS =
      """
      SELECT id, serial_number, model, measurement_unit, asset_id, status, status_changed_at,
             last_calibration_recorded_at, last_calibration_valid_until, created_at, updated_at,
             version
        FROM sensor
      """;

  private static final String FILTER =
      " WHERE (?::uuid IS NULL OR asset_id = ?::uuid) AND (?::text IS NULL OR status = ?::text)";

  private final JdbcClient jdbc;

  JdbcSensorRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public void insert(Sensor sensor) {
    try {
      jdbc.sql(
              """
              INSERT INTO sensor
                (id, serial_number, model, measurement_unit, asset_id, status, status_changed_at,
                 last_calibration_recorded_at, last_calibration_valid_until, created_at,
                 updated_at, version)
              VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
              """)
          .params(
              sensor.id(),
              sensor.serialNumber(),
              sensor.model(),
              sensor.measurementUnit(),
              sensor.assetId(),
              sensor.status().name(),
              JdbcSupport.timestamp(sensor.statusChangedAt()),
              JdbcSupport.timestamp(sensor.lastCalibrationRecordedAt()),
              JdbcSupport.timestamp(sensor.lastCalibrationValidUntil()),
              JdbcSupport.timestamp(sensor.createdAt()),
              JdbcSupport.timestamp(sensor.updatedAt()),
              sensor.version())
          .update();
    } catch (DuplicateKeyException e) {
      throw duplicateSerial(sensor);
    }
  }

  @Override
  public Optional<Sensor> findById(UUID id) {
    return jdbc.sql(COLUMNS + " WHERE id = ?").param(id).query(this::map).optional();
  }

  @Override
  public Sensor update(Sensor sensor) {
    int updated;
    try {
      updated =
          jdbc.sql(
                  """
                  UPDATE sensor
                     SET serial_number = ?, model = ?, asset_id = ?, status = ?,
                         status_changed_at = ?, last_calibration_recorded_at = ?,
                         last_calibration_valid_until = ?, updated_at = ?, version = version + 1
                   WHERE id = ? AND version = ?
                  """)
              .params(
                  sensor.serialNumber(),
                  sensor.model(),
                  sensor.assetId(),
                  sensor.status().name(),
                  JdbcSupport.timestamp(sensor.statusChangedAt()),
                  JdbcSupport.timestamp(sensor.lastCalibrationRecordedAt()),
                  JdbcSupport.timestamp(sensor.lastCalibrationValidUntil()),
                  JdbcSupport.timestamp(sensor.updatedAt()),
                  sensor.id(),
                  sensor.version())
              .update();
    } catch (DuplicateKeyException e) {
      throw duplicateSerial(sensor);
    }
    if (updated == 0) {
      throw new StaleVersionException("Sensor", sensor.id());
    }
    return sensor.withVersion(sensor.version() + 1);
  }

  @Override
  public List<DueSensor> findDueForCalibrationExpiry(Instant now, DueSensor after, int limit) {
    java.sql.Timestamp afterAt = after == null ? null : JdbcSupport.timestamp(after.validUntil());
    UUID afterId = after == null ? null : after.sensorId();
    return jdbc.sql(
            """
            SELECT id, last_calibration_valid_until
              FROM sensor
             WHERE status IN ('ACTIVE', 'INACTIVE')
               AND last_calibration_valid_until IS NOT NULL
               AND last_calibration_valid_until < ?
               AND (?::timestamptz IS NULL
                    OR (last_calibration_valid_until, id) > (?::timestamptz, ?::uuid))
             ORDER BY last_calibration_valid_until, id
             LIMIT ?
            """)
        .params(JdbcSupport.timestamp(now), afterAt, afterAt, afterId, limit)
        .query(
            (rs, row) ->
                new DueSensor(
                    rs.getObject("id", UUID.class),
                    JdbcSupport.instant(rs, "last_calibration_valid_until")))
        .list();
  }

  @Override
  public List<Sensor> findPage(UUID assetId, SensorStatus status, int page, int size) {
    String statusName = status == null ? null : status.name();
    return jdbc.sql(COLUMNS + FILTER + " ORDER BY lower(serial_number), id LIMIT ? OFFSET ?")
        .params(assetId, assetId, statusName, statusName, size, JdbcSupport.offset(page, size))
        .query(this::map)
        .list();
  }

  @Override
  public long count(UUID assetId, SensorStatus status) {
    String statusName = status == null ? null : status.name();
    return jdbc.sql("SELECT count(*) FROM sensor" + FILTER)
        .params(assetId, assetId, statusName, statusName)
        .query(Long.class)
        .single();
  }

  private static AlreadyExistsException duplicateSerial(Sensor sensor) {
    return new AlreadyExistsException(
        "SENSOR_SERIAL_DUPLICATED",
        "A sensor with serial number '" + sensor.serialNumber() + "' already exists");
  }

  private Sensor map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    return new Sensor(
        rs.getObject("id", UUID.class),
        rs.getString("serial_number"),
        rs.getString("model"),
        rs.getString("measurement_unit"),
        rs.getObject("asset_id", UUID.class),
        SensorStatus.valueOf(rs.getString("status")),
        JdbcSupport.instant(rs, "status_changed_at"),
        JdbcSupport.instant(rs, "last_calibration_recorded_at"),
        JdbcSupport.instant(rs, "last_calibration_valid_until"),
        JdbcSupport.instant(rs, "created_at"),
        JdbcSupport.instant(rs, "updated_at"),
        rs.getLong("version"));
  }
}
