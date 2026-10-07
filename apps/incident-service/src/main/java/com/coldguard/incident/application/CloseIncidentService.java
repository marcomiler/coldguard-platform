package com.coldguard.incident.application;

import com.coldguard.commons.messaging.EventActor;
import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.Role;
import com.coldguard.incident.domain.CloseEvidence;
import com.coldguard.incident.domain.Incident;
import com.coldguard.incident.domain.Transition;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CloseIncidentService {

  private final IncidentRepository repository;
  private final IncidentEventPublisher events;
  private final Clock clock;

  CloseIncidentService(IncidentRepository repository, IncidentEventPublisher events, Clock clock) {
    this.repository = repository;
    this.events = events;
    this.clock = clock;
  }

  @Transactional
  public Incident close(CloseIncidentCommand command) {
    Actor actor = Authorization.require(command.actor(), Role.MAINTENANCE_TECHNICIAN);
    CloseEvidence evidence = new CloseEvidence(command.cause(), command.resolutionComment());
    Incident incident =
        repository
            .findById(command.incidentId())
            .orElseThrow(() -> new IncidentNotFoundException(command.incidentId()));
    Transition transition = incident.close(actor.id(), evidence, clock);
    repository.update(transition.incident());
    events.publish(transition.event(), EventActor.user(actor.id()));
    return transition.incident();
  }
}
