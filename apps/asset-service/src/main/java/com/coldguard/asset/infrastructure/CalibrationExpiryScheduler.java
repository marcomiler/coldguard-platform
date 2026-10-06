package com.coldguard.asset.infrastructure;

import com.coldguard.asset.application.CalibrationExpiryService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs the calibration expiry check on the configured cron. Switched off with {@code
 * coldguard.asset.calibration-expiry.enabled=false}. Meant for a single instance: with several, two
 * runs could pick the same sensor, which the per-sensor re-check and the version guard make
 * harmless but wasteful.
 */
@Component
@ConditionalOnProperty(
    prefix = "coldguard.asset.calibration-expiry",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
class CalibrationExpiryScheduler {

  private final CalibrationExpiryService expiry;
  private final Counter moved;
  private final Counter failures;

  CalibrationExpiryScheduler(CalibrationExpiryService expiry, MeterRegistry meters) {
    this.expiry = expiry;
    this.moved =
        Counter.builder("coldguard.asset.calibration.expired")
            .description("Sensors moved to maintenance by the calibration expiry job")
            .register(meters);
    this.failures =
        Counter.builder("coldguard.asset.calibration.expiry.failures")
            .description("Sensors the calibration expiry job failed to move")
            .register(meters);
  }

  @Scheduled(cron = "${coldguard.asset.calibration-expiry.cron}")
  void run() {
    CalibrationExpiryService.Result result = expiry.run();
    moved.increment(result.transitioned());
    failures.increment(result.failed());
  }
}
