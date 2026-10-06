package com.coldguard.asset.infrastructure;

import com.coldguard.asset.application.CalibrationRepository;
import com.coldguard.asset.domain.CalibrationRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Insert-only: calibrations are evidence and are never changed or deleted. */
@Repository
class JdbcCalibrationRepository implements CalibrationRepository {

  private final JdbcClient jdbc;

  JdbcCalibrationRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public java.util.Optional<java.util.UUID> findLatestId(java.util.UUID sensorId) {
    return jdbc.sql(
            """
            SELECT id FROM calibration_record
             WHERE sensor_id = ?
             ORDER BY recorded_at DESC, id DESC
             LIMIT 1
            """)
        .param(sensorId)
        .query(java.util.UUID.class)
        .optional();
  }

  @Override
  public void insert(CalibrationRecord record) {
    jdbc.sql(
            """
            INSERT INTO calibration_record
              (id, sensor_id, kind, performed_at, valid_until, recorded_at, recorded_by, reason)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """)
        .params(
            record.id(),
            record.sensorId(),
            record.kind().name(),
            JdbcSupport.timestamp(record.performedAt()),
            JdbcSupport.timestamp(record.validUntil()),
            JdbcSupport.timestamp(record.recordedAt()),
            record.recordedBy(),
            record.reason())
        .update();
  }
}
