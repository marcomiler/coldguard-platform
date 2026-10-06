package com.coldguard.asset.application;

import com.coldguard.asset.domain.Asset;
import com.coldguard.asset.domain.CalibrationKind;
import com.coldguard.asset.domain.CalibrationRecord;
import com.coldguard.asset.domain.OperationalProfile;
import com.coldguard.asset.domain.ResourceNotFoundException;
import com.coldguard.asset.domain.Sensor;
import com.coldguard.asset.domain.SensorStatus;
import com.coldguard.commons.messaging.EventActor;
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

/**
 * Operational lifecycle of a sensor: status changes, calibrations, reassignment and retirement.
 * Every change needs a reason and leaves, in the same transaction, a history entry with who, when,
 * why and the value before and after, plus its events through the outbox. The transition rules
 * themselves live in {@link Sensor}.
 */
@Service
public class SensorLifecycleService {

  private final AssetRepository assets;
  private final SensorRepository sensors;
  private final OperationalProfileRepository profiles;
  private final CalibrationRepository calibrations;
  private final SensorHistoryRepository history;
  private final CalibrationPolicy calibrationPolicy;
  private final DomainEventPublisher events;
  private final Clock clock;

  public SensorLifecycleService(
      AssetRepository assets,
      SensorRepository sensors,
      OperationalProfileRepository profiles,
      CalibrationRepository calibrations,
      SensorHistoryRepository history,
      CalibrationPolicy calibrationPolicy,
      DomainEventPublisher events,
      Clock clock) {
    this.assets = assets;
    this.sensors = sensors;
    this.profiles = profiles;
    this.calibrations = calibrations;
    this.history = history;
    this.calibrationPolicy = calibrationPolicy;
    this.events = events;
    this.clock = clock;
  }

  /** Moving to RETIRED goes through the same path as {@link #retire}. */
  @Transactional
  public Sensor changeStatus(Actor actor, UUID sensorId, SensorStatus target, String reason) {
    AccessPolicy.requireAdministrator(actor);
    String why = Reasons.required(reason);
    Sensor before = find(sensorId);
    Instant now = clock.instant();
    Sensor saved = sensors.update(before.changeStatus(target, now));
    EventActor eventActor = Actors.toEventActor(actor);

    history.addEntry(
        HistoryEntries.of(
            actor,
            saved.id(),
            target == SensorStatus.RETIRED ? "RETIRED" : "STATUS_CHANGED",
            Map.of("status", before.status().name()),
            Map.of("status", saved.status().name()),
            why,
            now));
    if (target == SensorStatus.RETIRED) {
      events.publish(AssetEvents.sensorRetired(before, saved, why, eventActor, now));
    }
    events.publish(AssetEvents.sensorStatusChanged(before, saved, why, eventActor, now));
    return saved;
  }

  /** Logical retirement: the sensor and its whole history stay; nothing is ever deleted. */
  @Transactional
  public Sensor retire(Actor actor, UUID sensorId, String reason) {
    return changeStatus(actor, sensorId, SensorStatus.RETIRED, reason);
  }

  /**
   * Records a calibration or verification. It never changes the status: returning to ACTIVE is a
   * separate, explicit {@link #changeStatus}. The expiry is derived, never supplied.
   */
  @Transactional
  public CalibrationRecord recordCalibration(
      Actor actor, UUID sensorId, CalibrationKind kind, Instant performedAt, String reason) {
    AccessPolicy.requireAdministrator(actor);
    if (kind == null) {
      throw new IllegalArgumentException("calibration kind is required");
    }
    if (performedAt == null) {
      throw new IllegalArgumentException("performedAt is required");
    }
    Sensor before = find(sensorId);
    Instant now = clock.instant();
    Duration profileValidity =
        profiles.findBySensorId(sensorId).map(OperationalProfile::calibrationValidity).orElse(null);
    Instant validUntil = calibrationPolicy.validUntil(performedAt, profileValidity, now);
    CalibrationRecord record =
        new CalibrationRecord(
            UUID.randomUUID(), sensorId, kind, performedAt, validUntil, now, actor.id(), reason);

    Sensor saved = sensors.update(before.withCalibration(now, validUntil, now));
    calibrations.insert(record);
    history.addEntry(
        HistoryEntries.of(
            actor,
            saved.id(),
            "CALIBRATION_RECORDED",
            lastCalibration(before),
            calibrationSnapshot(record),
            record.reason(),
            now));
    events.publish(AssetEvents.sensorCalibrationRecorded(record, Actors.toEventActor(actor), now));
    return record;
  }

  /** Moves the sensor to another asset; only while in maintenance, and it stays in maintenance. */
  @Transactional
  public Sensor reassign(Actor actor, UUID sensorId, UUID targetAssetId, String reason) {
    AccessPolicy.requireAdministrator(actor);
    String why = Reasons.required(reason);
    Sensor before = find(sensorId);
    Asset target =
        assets
            .findById(targetAssetId)
            .orElseThrow(() -> new ResourceNotFoundException("Asset", targetAssetId));
    Instant now = clock.instant();
    Sensor saved = sensors.update(before.reassignTo(target.id(), now));

    history.addAssignment(
        new AssignmentEntry(
            UUID.randomUUID(), saved.id(), target.id(), before.assetId(), now, actor.id(), why));
    history.addEntry(
        HistoryEntries.of(
            actor,
            saved.id(),
            "REASSIGNED",
            Map.of("assetId", before.assetId().toString()),
            Map.of("assetId", saved.assetId().toString()),
            why,
            now));
    events.publish(
        AssetEvents.sensorReassigned(before, saved, why, Actors.toEventActor(actor), now));
    return saved;
  }

  private Sensor find(UUID sensorId) {
    return sensors
        .findById(sensorId)
        .orElseThrow(() -> new ResourceNotFoundException("Sensor", sensorId));
  }

  private static Map<String, Object> lastCalibration(Sensor sensor) {
    if (sensor.lastCalibrationRecordedAt() == null) {
      return null;
    }
    Map<String, Object> data = new LinkedHashMap<>();
    data.put("recordedAt", sensor.lastCalibrationRecordedAt().toString());
    data.put("validUntil", sensor.lastCalibrationValidUntil().toString());
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
