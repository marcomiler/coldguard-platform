package com.coldguard.asset.infrastructure;

import com.coldguard.asset.application.OperationalProfileRepository;
import com.coldguard.asset.domain.OperationalProfile;
import com.coldguard.asset.domain.StaleVersionException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class JdbcOperationalProfileRepository implements OperationalProfileRepository {

  private static final String SELECT =
      "SELECT "
          + ProfileRows.columns("operational_profile", "")
          + " FROM operational_profile WHERE sensor_id = ?";

  private final JdbcClient jdbc;

  JdbcOperationalProfileRepository(JdbcClient jdbc) {
    this.jdbc = jdbc;
  }

  @Override
  public Optional<OperationalProfile> findBySensorId(UUID sensorId) {
    return jdbc.sql(SELECT).param(sensorId).query(this::map).optional();
  }

  @Override
  public OperationalProfile save(OperationalProfile profile, long expectedVersion) {
    if (expectedVersion == 0) {
      try {
        jdbc.sql(
                """
                INSERT INTO operational_profile
                  (sensor_id, min_temperature, max_temperature, unit, magnitude_medium_from,
                   magnitude_high_from, magnitude_critical_from, persistence_min_consecutive,
                   persistence_window_seconds, expected_interval_seconds,
                   calibration_validity_seconds, updated_at, updated_by, version)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """)
            .params(values(profile))
            .update();
      } catch (DuplicateKeyException e) {
        throw new StaleVersionException("OperationalProfile", profile.sensorId());
      }
      return profile.withVersion(1);
    }
    Object[] values = values(profile);
    Object[] withKey = java.util.Arrays.copyOfRange(values, 1, values.length);
    Object[] params = java.util.Arrays.copyOf(withKey, withKey.length + 2);
    params[withKey.length] = profile.sensorId();
    params[withKey.length + 1] = expectedVersion;
    int updated =
        jdbc.sql(
                """
                UPDATE operational_profile
                   SET min_temperature = ?, max_temperature = ?, unit = ?,
                       magnitude_medium_from = ?, magnitude_high_from = ?,
                       magnitude_critical_from = ?, persistence_min_consecutive = ?,
                       persistence_window_seconds = ?, expected_interval_seconds = ?,
                       calibration_validity_seconds = ?, updated_at = ?, updated_by = ?,
                       version = version + 1
                 WHERE sensor_id = ? AND version = ?
                """)
            .params(params)
            .update();
    if (updated == 0) {
      throw new StaleVersionException("OperationalProfile", profile.sensorId());
    }
    return profile.withVersion(expectedVersion + 1);
  }

  private static Object[] values(OperationalProfile p) {
    return new Object[] {
      p.sensorId(),
      p.minTemperature(),
      p.maxTemperature(),
      p.unit(),
      p.magnitudeMediumFrom(),
      p.magnitudeHighFrom(),
      p.magnitudeCriticalFrom(),
      p.persistenceMinConsecutive(),
      (int) p.persistenceWindow().toSeconds(),
      (int) p.expectedInterval().toSeconds(),
      p.calibrationValidity() == null ? null : p.calibrationValidity().toSeconds(),
      JdbcSupport.timestamp(p.updatedAt()),
      p.updatedBy()
    };
  }

  private OperationalProfile map(java.sql.ResultSet rs, int row) throws java.sql.SQLException {
    return ProfileRows.read(rs, "");
  }
}
