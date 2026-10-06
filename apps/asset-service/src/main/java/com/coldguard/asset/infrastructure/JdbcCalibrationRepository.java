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
