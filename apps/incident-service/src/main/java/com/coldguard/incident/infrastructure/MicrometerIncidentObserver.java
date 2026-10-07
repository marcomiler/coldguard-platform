package com.coldguard.incident.infrastructure;

import com.coldguard.commons.observability.BusinessEventLogger;
import com.coldguard.incident.application.IncidentObserver;
import com.coldguard.incident.domain.Incident;
import com.coldguard.incident.domain.IncidentEvent;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * After commit: one business-event log line and one counter per transition. Counters are tagged by
 * priority only; incident ids never become labels (they are in the log line instead).
 */
@Component
class MicrometerIncidentObserver implements IncidentObserver {

  private final MeterRegistry registry;
  private final BusinessEventLogger businessEvents;

  MicrometerIncidentObserver(MeterRegistry registry, BusinessEventLogger businessEvents) {
    this.registry = registry;
    this.businessEvents = businessEvents;
  }

  @Override
  public void transitioned(IncidentEvent event) {
    Incident incident = event.incident();
    String name =
        switch (event) {
          case IncidentEvent.Created ignored -> "IncidentCreated";
          case IncidentEvent.Acknowledged ignored -> "IncidentAcknowledged";
          case IncidentEvent.Escalated ignored -> "IncidentEscalated";
          case IncidentEvent.Closed ignored -> "IncidentClosed";
          case IncidentEvent.OccurrenceRegistered ignored -> null;
        };
    if (name == null) {
      return; // not a catalogued event
    }
    businessEvents.log(
        name,
        Map.of(
            "incidentId", incident.id(),
            "assetId", incident.assetId(),
            "sensorId", incident.sensorId(),
            "priority", incident.priority().name()));
    afterCommit(
        () ->
            registry.counter(meterName(event), "priority", incident.priority().name()).increment());
  }

  /**
   * "created" is spelled "opened": the Prometheus client reserves the {@code _created} suffix, and
   * a counter named {@code ...created} would be exported as {@code coldguard_incident_total}.
   */
  private static String meterName(IncidentEvent event) {
    return switch (event) {
      case IncidentEvent.Created ignored -> "coldguard.incident.opened";
      case IncidentEvent.Acknowledged ignored -> "coldguard.incident.acknowledged";
      case IncidentEvent.Escalated ignored -> "coldguard.incident.escalated";
      case IncidentEvent.Closed ignored -> "coldguard.incident.closed";
      case IncidentEvent.OccurrenceRegistered ignored -> throw new IllegalStateException();
    };
  }

  private static void afterCommit(Runnable action) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              action.run();
            }
          });
    } else {
      action.run();
    }
  }
}
