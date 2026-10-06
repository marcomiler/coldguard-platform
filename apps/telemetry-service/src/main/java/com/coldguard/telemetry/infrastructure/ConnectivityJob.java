package com.coldguard.telemetry.infrastructure;

import com.coldguard.telemetry.application.ConnectivityMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs the connectivity check periodically; a failed pass is retried on the next one. */
@Component
@ConditionalOnProperty(
    name = "coldguard.telemetry.connectivity.enabled",
    havingValue = "true",
    matchIfMissing = true)
class ConnectivityJob {

  private static final Logger log = LoggerFactory.getLogger(ConnectivityJob.class);

  private final ConnectivityMonitor monitor;

  ConnectivityJob(ConnectivityMonitor monitor) {
    this.monitor = monitor;
  }

  @Scheduled(
      fixedDelayString = "${coldguard.telemetry.connectivity.check-interval:30s}",
      initialDelayString = "${coldguard.telemetry.connectivity.check-interval:30s}")
  void run() {
    try {
      monitor.check();
    } catch (RuntimeException e) {
      log.error("Connectivity check failed: {}", e.getClass().getSimpleName());
    }
  }
}
