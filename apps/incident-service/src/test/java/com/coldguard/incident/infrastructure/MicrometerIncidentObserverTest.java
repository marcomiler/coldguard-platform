package com.coldguard.incident.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;

import com.coldguard.commons.observability.BusinessEventLogger;
import com.coldguard.incident.domain.Criticality;
import com.coldguard.incident.domain.DomainFixtures;
import com.coldguard.incident.domain.Incident;
import com.coldguard.incident.domain.IncidentEvent;
import com.coldguard.incident.domain.Magnitude;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class MicrometerIncidentObserverTest {

  private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
  private final MicrometerIncidentObserver observer =
      new MicrometerIncidentObserver(registry, new BusinessEventLogger());

  @AfterEach
  void clean() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  private static Incident incident() {
    return DomainFixtures.open(Criticality.CRITICAL, Magnitude.CRITICAL, true);
  }

  private double count(String name, String priority) {
    var counter = registry.find(name).tag("priority", priority).counter();
    return counter == null ? 0 : counter.count();
  }

  @Test
  void eachCataloguedTransitionCountsOnceByPriority() {
    Incident incident = incident();

    observer.transitioned(new IncidentEvent.Created(incident));
    observer.transitioned(new IncidentEvent.Acknowledged(incident));
    observer.transitioned(new IncidentEvent.Escalated(incident, "reason"));
    observer.transitioned(new IncidentEvent.Closed(incident));

    assertThat(count("coldguard.incident.opened", "P1")).isEqualTo(1);
    assertThat(count("coldguard.incident.acknowledged", "P1")).isEqualTo(1);
    assertThat(count("coldguard.incident.escalated", "P1")).isEqualTo(1);
    assertThat(count("coldguard.incident.closed", "P1")).isEqualTo(1);
  }

  @Test
  void anOccurrenceIsNotACataloguedEvent() {
    Incident incident = incident();

    observer.transitioned(new IncidentEvent.OccurrenceRegistered(incident, incident.priority()));

    assertThat(registry.getMeters()).isEmpty();
  }

  @Test
  void nothingIsCountedUntilTheTransactionCommits() {
    TransactionSynchronizationManager.initSynchronization();

    observer.transitioned(new IncidentEvent.Created(incident()));
    assertThat(count("coldguard.incident.opened", "P1")).isZero();

    TransactionSynchronizationManager.getSynchronizations()
        .forEach(TransactionSynchronization::afterCommit);
    assertThat(count("coldguard.incident.opened", "P1")).isEqualTo(1);
  }

  @Test
  void aRolledBackTransitionIsNeverCounted() {
    TransactionSynchronizationManager.initSynchronization();

    observer.transitioned(new IncidentEvent.Closed(incident()));
    TransactionSynchronizationManager.getSynchronizations()
        .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

    assertThat(count("coldguard.incident.closed", "P1")).isZero();
  }
}
