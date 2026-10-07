package com.coldguard.commons.observability;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationPredicate;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.scheduling.support.ScheduledTaskObservationContext;

/**
 * Keeps the traces about the platform's work. The Prometheus scrape and the health probes would
 * otherwise be most of the spans, and the Outbox relay and the housekeeping tasks (run many times
 * per second or hour with nothing to show) would bury the real traces. Metrics and the other
 * scheduled tasks (connectivity, calibration expiry) are not affected.
 */
public class NoiseFreeObservations implements ObservationPredicate {

  @Override
  public boolean test(String name, Observation.Context context) {
    if (context instanceof ServerRequestObservationContext http) {
      String path = http.getCarrier().getRequestURI();
      return path == null || !path.startsWith("/actuator");
    }
    if (context instanceof ScheduledTaskObservationContext task) {
      String owner = task.getTargetClass().getSimpleName();
      return !(owner.startsWith("OutboxRelay") || owner.startsWith("InboxGuard"));
    }
    return true;
  }
}
