package com.coldguard.incident.application;

import com.coldguard.commons.messaging.EventActor;
import com.coldguard.incident.domain.Incident;
import com.coldguard.incident.domain.SlaPolicy;
import com.coldguard.incident.domain.Transition;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Opens an incident and emits its events; shared by manual creation and automatic detection. */
@Component
class IncidentOpener {

  private final IncidentRepository repository;
  private final SlaPolicy slaPolicy;
  private final IncidentEventPublisher events;
  private final Clock clock;

  IncidentOpener(
      IncidentRepository repository,
      SlaPolicy slaPolicy,
      IncidentEventPublisher events,
      Clock clock) {
    this.repository = repository;
    this.slaPolicy = slaPolicy;
    this.events = events;
    this.clock = clock;
  }

  /**
   * @throws DuplicateIncidentException if a concurrent transaction opened the same incident first
   */
  @Transactional
  public Incident open(OpenIncidentCommand command, EventActor actor) {
    Transition opened =
        Incident.open(
            UUID.randomUUID().toString(),
            command.assetId(),
            command.sensorId(),
            command.anomalyType(),
            command.assetCriticality(),
            command.magnitude(),
            command.persistent(),
            command.sourceReadingId(),
            slaPolicy,
            clock);
    repository.save(opened.incident());
    events.publish(opened.event(), actor);
    return opened.incident();
  }
}
