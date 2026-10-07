package com.coldguard.incident.application;

import com.coldguard.commons.messaging.EventActor;
import com.coldguard.incident.domain.Incident;
import com.coldguard.incident.domain.SlaPolicy;
import com.coldguard.incident.domain.Transition;
import java.time.Clock;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reacts to a threshold breach: opens an incident, or registers another occurrence on the open
 * equivalent one. If a concurrent transaction opens the same incident first, the insert fails with
 * {@link DuplicateIncidentException}; the whole delivery rolls back and its retry takes the update
 * branch.
 */
@Service
public class HandleThresholdBreachService {

  private static final EventActor ACTOR = EventActor.system("incident-service");

  private final IncidentRepository repository;
  private final IncidentOpener opener;
  private final IncidentEventPublisher events;
  private final SlaPolicy slaPolicy;
  private final Clock clock;

  HandleThresholdBreachService(
      IncidentRepository repository,
      IncidentOpener opener,
      IncidentEventPublisher events,
      SlaPolicy slaPolicy,
      Clock clock) {
    this.repository = repository;
    this.opener = opener;
    this.events = events;
    this.slaPolicy = slaPolicy;
    this.clock = clock;
  }

  @Transactional
  public void handle(OpenIncidentCommand command) {
    Optional<Incident> open =
        repository.findOpen(command.assetId(), command.sensorId(), command.anomalyType());
    if (open.isEmpty()) {
      opener.open(command, ACTOR);
      return;
    }
    Transition updated =
        open.get().registerOccurrence(command.magnitude(), command.persistent(), slaPolicy, clock);
    repository.update(updated.incident());
    events.publish(updated.event(), ACTOR);
  }
}
