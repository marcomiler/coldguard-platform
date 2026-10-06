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

  /**
   * Moves the sensor to {@code target}. RETIRED is terminal, a sensor is never "moved" to the
   * status it already has, and returning to ACTIVE needs valid calibration evidence: after
   * maintenance a calibration recorded since entering it (a previous valid one is not enough),
   * after INACTIVE a calibration that has not expired.
   */
  public Sensor changeStatus(SensorStatus target, Instant now) {
    if (status == SensorStatus.RETIRED) {
      throw new SensorTransitionNotAllowedException(
          "The sensor is retired; retirement is terminal");
    }
    if (target == status) {
      throw new SensorTransitionNotAllowedException("The sensor is already " + status);
    }
    switch (target) {
      case ACTIVE -> requireEvidenceToActivate(now);
      case IN_MAINTENANCE, INACTIVE, RETIRED -> {
        // Allowed from any other non-terminal status, by administrative decision.
      }
    }
    return withStatus(target, now);
  }

  private void requireEvidenceToActivate(Instant now) {
    switch (status) {
      case IN_MAINTENANCE -> {
        if (lastCalibrationRecordedAt == null
            || !lastCalibrationRecordedAt.isAfter(statusChangedAt)) {
          throw new CalibrationEvidenceRequiredException();
        }
        requireCalibrationNotExpired(now);
      }
      case INACTIVE -> requireCalibrationNotExpired(now);
      case ACTIVE, RETIRED ->
          throw new IllegalStateException("Unreachable: handled before the evidence check");
    }
  }

  private void requireCalibrationNotExpired(Instant now) {
    if (lastCalibrationValidUntil == null) {
      throw new CalibrationExpiredException("The sensor has no calibration or verification");
    }
    if (!lastCalibrationValidUntil.isAfter(now)) {
      throw new CalibrationExpiredException(
          "The last calibration or verification expired at " + lastCalibrationValidUntil);
    }
  }

  /** Attaches the sensor to another asset; allowed only in maintenance. The status is unchanged. */
  public Sensor reassignTo(UUID newAssetId, Instant now) {
    if (status != SensorStatus.IN_MAINTENANCE) {
      throw new ReassignmentNotAllowedException(
          "A sensor can only be reassigned while in maintenance (it is " + status + ")");
    }
    if (assetId.equals(newAssetId)) {
      throw new ReassignmentNotAllowedException("The sensor is already assigned to that asset");
    }
    return new Sensor(
        id,
        serialNumber,
        model,
        measurementUnit,
        newAssetId,
        status,
        statusChangedAt,
        lastCalibrationRecordedAt,
        lastCalibrationValidUntil,
        createdAt,
        now,
        version);
  }

  /** Recording a calibration never changes the status; a retired sensor's evidence is frozen. */
  public Sensor withCalibration(Instant recordedAt, Instant validUntil, Instant now) {
    if (status == SensorStatus.RETIRED) {
      throw new SensorTransitionNotAllowedException(
          "The sensor is retired; its calibration history cannot change");
    }
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

  private Sensor withStatus(SensorStatus target, Instant now) {
    return new Sensor(
        id,
        serialNumber,
        model,
        measurementUnit,
        assetId,
        target,
        now,
        lastCalibrationRecordedAt,
        lastCalibrationValidUntil,
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
