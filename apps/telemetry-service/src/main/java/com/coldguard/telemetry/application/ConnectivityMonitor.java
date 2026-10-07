package com.coldguard.telemetry.application;

import com.coldguard.commons.messaging.EventActor;
import com.coldguard.commons.messaging.outbox.DomainEventPublisher;
import com.coldguard.commons.observability.BusinessEventLogger;
import com.coldguard.telemetry.domain.SensorCondition;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Finds ACTIVE sensors that have stopped reporting and announces each loss once. Losing
 * connectivity creates no incident and does not change the sensor's status; the flag is cleared
 * when a reading arrives again.
 */
public class ConnectivityMonitor {

  static final EventActor ACTOR = EventActor.system("connectivity-monitor");

  private final SensorConditionRepository conditions;
  private final DomainEventPublisher events;
  private final ConnectivityMetrics metrics;
  private final BusinessEventLogger businessEvents;
  private final Clock clock;
  private final TransactionTemplate transaction;
  private final double toleranceFactor;
  private final int batchSize;

  public ConnectivityMonitor(
      SensorConditionRepository conditions,
      DomainEventPublisher events,
      ConnectivityMetrics metrics,
      BusinessEventLogger businessEvents,
      Clock clock,
      PlatformTransactionManager transactionManager,
      double toleranceFactor,
      int batchSize) {
    this.conditions = conditions;
    this.events = events;
    this.metrics = metrics;
    this.businessEvents = businessEvents;
    this.clock = clock;
    this.transaction = new TransactionTemplate(transactionManager);
    this.toleranceFactor = toleranceFactor;
    this.batchSize = batchSize;
  }

  /**
   * One pass over the overdue sensors, in batches (one transaction each, so a failure loses only
   * its batch and the event commits with the flag).
   *
   * @return how many sensors were flagged
   */
  public int check() {
    Instant now = clock.instant();
    int flagged = 0;
    int batch;
    do {
      Integer done = transaction.execute(status -> flagBatch(now));
      batch = done == null ? 0 : done;
      flagged += batch;
    } while (batch == batchSize);
    return flagged;
  }

  private int flagBatch(Instant now) {
    List<SensorCondition> overdue = conditions.lockOverdue(now, toleranceFactor, batchSize);
    for (SensorCondition condition : overdue) {
      conditions.save(condition.connectivityLost(now));
      events.publish(TelemetryEvents.connectivityLost(condition, now, ACTOR));
      metrics.connectivityLost();
      businessEvents.log(
          "SensorConnectivityLost",
          java.util.Map.of(
              "sensorId", condition.sensorId().toString(),
              "assetId", condition.assetId().toString()));
    }
    return overdue.size();
  }
}
