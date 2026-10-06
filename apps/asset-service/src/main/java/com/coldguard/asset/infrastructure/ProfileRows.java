package com.coldguard.asset.infrastructure;

import com.coldguard.asset.domain.OperationalProfile;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;

/** Reads an {@code operational_profile} row, optionally from aliased columns of a join. */
final class ProfileRows {

  private ProfileRows() {}

  /** The profile columns, each aliased with {@code prefix} when it is not empty. */
  static String columns(String table, String prefix) {
    StringBuilder sql = new StringBuilder();
    for (String column :
        new String[] {
          "sensor_id",
          "min_temperature",
          "max_temperature",
          "unit",
          "magnitude_medium_from",
          "magnitude_high_from",
          "magnitude_critical_from",
          "persistence_min_consecutive",
          "persistence_window_seconds",
          "expected_interval_seconds",
          "calibration_validity_seconds",
          "updated_at",
          "updated_by",
          "version"
        }) {
      if (sql.length() > 0) {
        sql.append(", ");
      }
      sql.append(table).append('.').append(column);
      if (!prefix.isEmpty()) {
        sql.append(" AS ").append(prefix).append(column);
      }
    }
    return sql.toString();
  }

  /** Null when the (left-joined) row has no profile. */
  static OperationalProfile read(ResultSet rs, String prefix) throws SQLException {
    UUID sensorId = rs.getObject(prefix + "sensor_id", UUID.class);
    if (sensorId == null) {
      return null;
    }
    long validity = rs.getLong(prefix + "calibration_validity_seconds");
    boolean noValidity = rs.wasNull();
    return new OperationalProfile(
        sensorId,
        rs.getBigDecimal(prefix + "min_temperature"),
        rs.getBigDecimal(prefix + "max_temperature"),
        rs.getString(prefix + "unit"),
        rs.getBigDecimal(prefix + "magnitude_medium_from"),
        rs.getBigDecimal(prefix + "magnitude_high_from"),
        rs.getBigDecimal(prefix + "magnitude_critical_from"),
        rs.getInt(prefix + "persistence_min_consecutive"),
        Duration.ofSeconds(rs.getInt(prefix + "persistence_window_seconds")),
        Duration.ofSeconds(rs.getInt(prefix + "expected_interval_seconds")),
        noValidity ? null : Duration.ofSeconds(validity),
        JdbcSupport.instant(rs, prefix + "updated_at"),
        rs.getString(prefix + "updated_by"),
        rs.getLong(prefix + "version"));
  }
}
