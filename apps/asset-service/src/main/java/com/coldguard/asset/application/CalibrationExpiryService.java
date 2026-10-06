package com.coldguard.asset.application;

import com.coldguard.asset.domain.Sensor;
import com.coldguard.asset.domain.SensorStatus;
import com.coldguard.commons.messaging.outbox.DomainEventPublisher;
import com.coldguard.commons.security.Actor;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Moves sensors whose calibration or verification has expired to IN_MAINTENANCE (RN-018). The
 * transition runs directly, as the system actor {@code calibration-expiry-job}, with a fixed
 * reason.
 *
 * <p>Each sensor is handled in its own transaction, so one failing sensor never rolls back the
 * others. Eligibility is re-checked inside that transaction (an administrator may have acted since
 * the sensor was selected), and a sensor already in maintenance is never selected again, so running
 * the job twice is harmless. Sensors are walked by keyset, which guarantees the run ends even if
 * some sensor fails every time.
 */
public class CalibrationExpiryService {

  /** The reason recorded for every automatic transition (text of the use case). */
  public static final String REASON = "calibración/verificación vencida";

  static final Actor JOB = Actor.system("calibration-expiry-job");

  private static final Logger log = LoggerFactory.getLogger(CalibrationExpiryService.class);

  /** Outcome of one run. */
  public record Result(int examined, int transitioned, int failed) {}

  private final SensorRepository sensors;
  private final CalibrationRepository calibrations;
  private final SensorHistoryRepository history;
  private final DomainEventPublisher events;
  private final Clock clock;
  private final TransactionTemplate perSensor;
  private final int batchSize;

  public CalibrationExpiryService(
      SensorRepository sensors,
      CalibrationRepository calibrations,
      SensorHistoryRepository history,
      DomainEventPublisher events,
      Clock clock,
      PlatformTransactionManager transactionManager,
      int batchSize) {
    if (batchSize < 1) {
      throw new IllegalArgumentException("batchSize must be at least 1");
    }
    this.sensors = sensors;
    this.calibrations = calibrations;
    this.history = history;
    this.events = events;
    this.clock = clock;
    this.batchSize = batchSize;
    this.perSensor = new TransactionTemplate(transactionManager);
    this.perSensor.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  public Result run() {
    Instant runStartedAt = clock.instant();
    DueSensor after = null;
    int examined = 0;
    int transitioned = 0;
    int failed = 0;
    while (true) {
      List<DueSensor> batch = sensors.findDueForCalibrationExpiry(runStartedAt, after, batchSize);
      for (DueSensor due : batch) {
        examined++;
        try {
          if (Boolean.TRUE.equals(perSensor.execute(status -> expire(due)))) {
            transitioned++;
          }
        } catch (RuntimeException e) {
          failed++;
          log.warn(
              "Calibration expiry failed for sensor {}; it is retried on the next run",
              due.sensorId(),
              e);
        }
      }
      if (batch.size() < batchSize) {
        break;
      }
      after = batch.get(batch.size() - 1);
    }
    if (transitioned > 0 || failed > 0) {
      log.info(
          "Calibration expiry: {} examined, {} moved to maintenance, {} failed",
          examined,
          transitioned,
          failed);
    }
    return new Result(examined, transitioned, failed);
  }

  /**
   * @return false when the sensor is no longer eligible, true once it was moved
   */
  private boolean expire(DueSensor due) {
    Instant now = clock.instant();
    Sensor before = sensors.findById(due.sensorId()).orElse(null);
    if (before == null || !isEligible(before, now)) {
      return false;
    }
    UUID calibrationId =
        calibrations
            .findLatestId(before.id())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Sensor "
                            + before.id()
                            + " has a calibration expiry but no calibration record"));
    Sensor saved = sensors.update(before.changeStatus(SensorStatus.IN_MAINTENANCE, now));

    history.addEntry(
        HistoryEntries.of(
            JOB,
            saved.id(),
            "STATUS_CHANGED",
            Map.of("status", before.status().name()),
            Map.of("status", saved.status().name()),
            REASON,
            now));
    events.publish(
        AssetEvents.sensorCalibrationExpired(
            saved.id(),
            calibrationId,
            before.lastCalibrationValidUntil(),
            now,
            Actors.toEventActor(JOB)));
    events.publish(
        AssetEvents.sensorStatusChanged(before, saved, REASON, Actors.toEventActor(JOB), now));
    return true;
  }

  private static boolean isEligible(Sensor sensor, Instant now) {
    return (sensor.status() == SensorStatus.ACTIVE || sensor.status() == SensorStatus.INACTIVE)
        && sensor.lastCalibrationValidUntil() != null
        && sensor.lastCalibrationValidUntil().isBefore(now);
  }
}
