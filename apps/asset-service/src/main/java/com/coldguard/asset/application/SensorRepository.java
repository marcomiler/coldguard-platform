package com.coldguard.asset.application;

import com.coldguard.asset.domain.Sensor;
import com.coldguard.asset.domain.SensorStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SensorRepository {

  /**
   * @throws com.coldguard.asset.domain.AlreadyExistsException if the serial number is taken
   */
  void insert(Sensor sensor);

  Optional<Sensor> findById(UUID id);

  /**
   * Persists the sensor if its stored version still equals {@code sensor.version()} and returns it
   * with the new version.
   *
   * @throws com.coldguard.asset.domain.StaleVersionException if it changed in between
   * @throws com.coldguard.asset.domain.AlreadyExistsException if the new serial number is taken
   */
  Sensor update(Sensor sensor);

  /**
   * ACTIVE or INACTIVE sensors whose last calibration expired before {@code now}, ordered by {@code
   * (expiry, id)} and strictly after {@code after} (null starts at the beginning). Sensors without
   * any calibration are never returned.
   */
  List<DueSensor> findDueForCalibrationExpiry(Instant now, DueSensor after, int limit);

  /** Null filters are ignored. */
  List<Sensor> findPage(UUID assetId, SensorStatus status, int page, int size);

  long count(UUID assetId, SensorStatus status);
}
