package com.coldguard.asset.application;

import com.coldguard.asset.domain.Asset;
import com.coldguard.asset.domain.CalibrationKind;
import com.coldguard.asset.domain.CalibrationRecord;
import com.coldguard.asset.domain.ResourceNotFoundException;
import com.coldguard.asset.domain.Sensor;
import com.coldguard.asset.domain.SensorStatus;
import com.coldguard.asset.domain.StaleVersionException;
import com.coldguard.commons.messaging.outbox.DomainEventPublisher;
import com.coldguard.commons.security.Actor;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registration and technical data of sensors. Lifecycle changes live in their own use cases. */
@Service
public class SensorService {

  /** A calibration supplied while registering a sensor. */
  public record InitialCalibration(CalibrationKind kind, Instant performedAt, String reason) {}

  private final AssetRepository assets;
  private final SensorRepository sensors;
  private final CalibrationRepository calibrations;
  private final SensorHistoryRepository history;
  private final OperationalProfileService profiles;
  private final CalibrationPolicy calibrationPolicy;
  private final DomainEventPublisher events;
  private final PageRequestPolicy paging;
  private final Clock clock;

  public SensorService(
      AssetRepository assets,
      SensorRepository sensors,
      CalibrationRepository calibrations,
      SensorHistoryRepository history,
      OperationalProfileService profiles,
      CalibrationPolicy calibrationPolicy,
      DomainEventPublisher events,
      PageRequestPolicy paging,
      Clock clock) {
    this.assets = assets;
    this.sensors = sensors;
    this.calibrations = calibrations;
    this.history = history;
    this.profiles = profiles;
    this.calibrationPolicy = calibrationPolicy;
    this.events = events;
    this.paging = paging;
    this.clock = clock;
  }

  /**
   * Registers an ACTIVE sensor attached to {@code assetId}. The initial calibration and the profile
   * are optional; a calibration needs a validity, from the profile given here or the default.
   *
   * @param profile built for a placeholder sensor id; only its values are used
   */
  @Transactional
  public Sensor register(
      Actor actor,
      UUID assetId,
      String serialNumber,
      String model,
      String measurementUnit,
      InitialCalibration initialCalibration,
      OperationalProfileDraft profile) {
    AccessPolicy.requireAdministrator(actor);
    Asset asset =
        assets.findById(assetId).orElseThrow(() -> new ResourceNotFoundException("Asset", assetId));
    Instant now = clock.instant();
    Sensor sensor = Sensor.register(asset.id(), serialNumber, model, measurementUnit, now);

    CalibrationRecord calibration = null;
    if (initialCalibration != null) {
      Duration profileValidity = profile == null ? null : profile.calibrationValidity();
      Instant validUntil =
          calibrationPolicy.validUntil(initialCalibration.performedAt(), profileValidity, now);
      calibration =
          new CalibrationRecord(
              UUID.randomUUID(),
              sensor.id(),
              initialCalibration.kind(),
              initialCalibration.performedAt(),
              validUntil,
              now,
              actor.id(),
              initialCalibration.reason());
      sensor = sensor.withCalibration(now, validUntil, now);
    }

    sensors.insert(sensor);
    history.addAssignment(
        new AssignmentEntry(
            UUID.randomUUID(),
            sensor.id(),
            asset.id(),
            null,
            now,
            actor.id(),
            "Initial assignment"));
    history.addEntry(entry(actor, sensor, "REGISTERED", null, snapshot(sensor), null, now));

    if (calibration != null) {
      calibrations.insert(calibration);
      events.publish(
          AssetEvents.sensorCalibrationRecorded(calibration, Actors.toEventActor(actor), now));
      history.addEntry(
          entry(
              actor,
              sensor,
              "CALIBRATION_RECORDED",
              null,
              calibrationSnapshot(calibration),
              calibration.reason(),
              now));
    }
    if (profile != null) {
      profiles.apply(actor, sensor, profile.forSensor(sensor.id()), 0, now);
    }
    return sensor;
  }

  /**
   * Updates technical data; a null argument leaves that field as it is. A change that alters
   * nothing writes nothing.
   */
  @Transactional
  public Sensor update(
      Actor actor, UUID sensorId, long expectedVersion, String serialNumber, String model) {
    AccessPolicy.requireAdministrator(actor);
    Sensor before = find(sensorId);
    if (before.version() != expectedVersion) {
      throw new StaleVersionException("Sensor", sensorId);
    }
    Instant now = clock.instant();
    Sensor candidate = before.withTechnicalData(serialNumber, model, now);
    Map<String, Object> previous = technicalData(before);
    Map<String, Object> next = technicalData(candidate);
    if (previous.equals(next)) {
      return before;
    }
    Sensor saved = sensors.update(candidate);
    history.addEntry(entry(actor, saved, "TECHNICAL_DATA_UPDATED", previous, next, null, now));
    return saved;
  }

  @Transactional(readOnly = true)
  public Sensor get(Actor actor, UUID sensorId) {
    AccessPolicy.requireReader(actor);
    return find(sensorId);
  }

  @Transactional(readOnly = true)
  public Page<Sensor> list(
      Actor actor, UUID assetId, SensorStatus status, int page, int requestedSize) {
    AccessPolicy.requireReader(actor);
    int size = paging.sizeFor(requestedSize);
    paging.validate(page, size);
    return new Page<>(
        sensors.findPage(assetId, status, page, size), page, size, sensors.count(assetId, status));
  }

  private Sensor find(UUID sensorId) {
    return sensors
        .findById(sensorId)
        .orElseThrow(() -> new ResourceNotFoundException("Sensor", sensorId));
  }

  private static SensorHistoryEntry entry(
      Actor actor,
      Sensor sensor,
      String action,
      Map<String, Object> previous,
      Map<String, Object> next,
      String reason,
      Instant now) {
    return new SensorHistoryEntry(
        UUID.randomUUID(),
        sensor.id(),
        action,
        previous,
        next,
        reason,
        Actors.type(actor),
        actor.id(),
        now);
  }

  private static Map<String, Object> technicalData(Sensor sensor) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("serialNumber", sensor.serialNumber());
    data.put("model", sensor.model());
    return data;
  }

  private static Map<String, Object> snapshot(Sensor sensor) {
    Map<String, Object> data = technicalData(sensor);
    data.put("measurementUnit", sensor.measurementUnit());
    data.put("assetId", sensor.assetId().toString());
    data.put("status", sensor.status().name());
    return data;
  }

  private static Map<String, Object> calibrationSnapshot(CalibrationRecord record) {
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("calibrationId", record.id().toString());
    data.put("kind", record.kind().name());
    data.put("performedAt", record.performedAt().toString());
    data.put("validUntil", record.validUntil().toString());
    return data;
  }
}
