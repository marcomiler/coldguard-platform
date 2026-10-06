package com.coldguard.asset.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A sensor attached to one asset. The latest calibration is kept here, next to the status, because
 * both guard the transitions of the same aggregate.
 */
public record Sensor(
    UUID id,
    String serialNumber,
    String model,
    String measurementUnit,
    UUID assetId,
    SensorStatus status,
    Instant statusChangedAt,
    Instant lastCalibrationRecordedAt,
    Instant lastCalibrationValidUntil,
    Instant createdAt,
    Instant updatedAt,
    long version) {

  public Sensor {
    serialNumber = Text.required(serialNumber, "serialNumber", 80);
    model = Text.optional(model, "model", 80);
    measurementUnit = Text.required(measurementUnit, "measurementUnit", 20);
    if (status == null) {
      throw new IllegalArgumentException("status is required");
    }
  }

  /** A new sensor starts ACTIVE; a calibration is not required to register it. */
  public static Sensor register(
      UUID assetId, String serialNumber, String model, String measurementUnit, Instant now) {
    return new Sensor(
        UUID.randomUUID(),
        serialNumber,
        model,
        measurementUnit,
        assetId,
        SensorStatus.ACTIVE,
        now,
        null,
        null,
        now,
        now,
        1);
  }

  /** A null argument leaves the field as it is. */
  public Sensor withTechnicalData(String newSerialNumber, String newModel, Instant now) {
    return new Sensor(
        id,
        newSerialNumber == null ? serialNumber : newSerialNumber,
        newModel == null ? model : newModel,
        measurementUnit,
        assetId,
        status,
        statusChangedAt,
        lastCalibrationRecordedAt,
        lastCalibrationValidUntil,
        createdAt,
        now,
        version);
  }

  /** Recording a calibration never changes the status. */
  public Sensor withCalibration(Instant recordedAt, Instant validUntil, Instant now) {
    return new Sensor(
        id,
        serialNumber,
        model,
        measurementUnit,
        assetId,
        status,
        statusChangedAt,
        recordedAt,
        validUntil,
        createdAt,
        now,
        version);
  }

  public Sensor withVersion(long newVersion) {
    return new Sensor(
        id,
        serialNumber,
        model,
        measurementUnit,
        assetId,
        status,
        statusChangedAt,
        lastCalibrationRecordedAt,
        lastCalibrationValidUntil,
        createdAt,
        updatedAt,
        newVersion);
  }
}
