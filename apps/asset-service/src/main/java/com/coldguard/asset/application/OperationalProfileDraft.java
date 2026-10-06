package com.coldguard.asset.application;

import com.coldguard.asset.domain.OperationalProfile;
import com.coldguard.asset.domain.Sensor;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** A profile as the caller supplies it; the service stamps who and when. */
public record OperationalProfileDraft(
    UUID sensorId,
    BigDecimal minTemperature,
    BigDecimal maxTemperature,
    String unit,
    BigDecimal magnitudeMediumFrom,
    BigDecimal magnitudeHighFrom,
    BigDecimal magnitudeCriticalFrom,
    int persistenceMinConsecutive,
    Duration persistenceWindow,
    Duration expectedInterval,
    Duration calibrationValidity) {

  /**
   * The same values for another sensor; used when the sensor id is only known after creating it.
   */
  public OperationalProfileDraft forSensor(UUID newSensorId) {
    return new OperationalProfileDraft(
        newSensorId,
        minTemperature,
        maxTemperature,
        unit,
        magnitudeMediumFrom,
        magnitudeHighFrom,
        magnitudeCriticalFrom,
        persistenceMinConsecutive,
        persistenceWindow,
        expectedInterval,
        calibrationValidity);
  }

  /** Builds the validated profile; its unit must be the unit the sensor measures. */
  OperationalProfile toProfile(Sensor sensor, String updatedBy, Instant now) {
    OperationalProfile profile =
        new OperationalProfile(
            sensor.id(),
            minTemperature,
            maxTemperature,
            unit,
            magnitudeMediumFrom,
            magnitudeHighFrom,
            magnitudeCriticalFrom,
            persistenceMinConsecutive,
            persistenceWindow,
            expectedInterval,
            calibrationValidity,
            now,
            updatedBy,
            0);
    if (!profile.unit().equalsIgnoreCase(sensor.measurementUnit())) {
      throw new IllegalArgumentException(
          "profile unit must match the sensor's measurement unit ("
              + sensor.measurementUnit()
              + ")");
    }
    return profile;
  }
}
