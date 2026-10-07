package com.coldguard.incident.application;

import com.coldguard.commons.messaging.EventActor;
import com.coldguard.commons.security.Actor;
import com.coldguard.incident.domain.Incident;
import com.coldguard.incident.domain.IncidentAlreadyOpenException;
import org.springframework.stereotype.Service;

/**
 * Technical, manual creation: refuses to duplicate an open incident. Not itself transactional: the
 * insert runs in {@link IncidentOpener}'s transaction, so after a lost race (which aborts that
 * transaction) the winner can still be looked up.
 */
@Service
public class CreateIncidentService {

  private final IncidentRepository repository;
  private final IncidentOpener opener;

  CreateIncidentService(IncidentRepository repository, IncidentOpener opener) {
    this.repository = repository;
    this.opener = opener;
  }

  public Incident create(CreateIncidentCommand command) {
    OpenIncidentCommand anomaly = command.anomaly();
    repository
        .findOpen(anomaly.assetId(), anomaly.sensorId(), anomaly.anomalyType())
        .ifPresent(
            existing -> {
              throw new IncidentAlreadyOpenException(existing.id());
            });
    try {
      return opener.open(anomaly, eventActor(command.actor()));
    } catch (DuplicateIncidentException lostRace) {
      // The unique index is the final authority; report the winner.
      throw new IncidentAlreadyOpenException(
          repository
              .findOpen(anomaly.assetId(), anomaly.sensorId(), anomaly.anomalyType())
              .map(Incident::id)
              .orElseThrow(() -> lostRace));
    }
  }

  static EventActor eventActor(Actor actor) {
    return actor == null ? EventActor.system("incident-api") : EventActor.user(actor.id());
  }
}
