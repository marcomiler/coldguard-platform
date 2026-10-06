package com.coldguard.incident.application;

import com.coldguard.incident.domain.Incident;
import com.coldguard.incident.identity.domain.Role;
import org.springframework.stereotype.Service;

/**
 * {@code cause} and {@code resolutionComment} are required (RN-007) and validated as non-blank
 * here, but are not persisted yet — {@link Incident} and its JPA mapping have no columns for them.
 * Once validated they are discarded; a future migration is required to store them durably (needed
 * for RN-008 auditability once an audit log exists).
 */
@Service
public class CloseIncidentService {

  private final IncidentRepository incidentRepository;

  public CloseIncidentService(IncidentRepository incidentRepository) {
    this.incidentRepository = incidentRepository;
  }

  public Incident close(CloseIncidentCommand command) {
    Actor actor = command.actor();
    if (actor == null || !actor.hasRole(Role.MAINTENANCE_TECHNICIAN)) {
      throw new IncidentCloseForbiddenException(actor);
    }
    requireNonBlank(command.cause(), "cause is required");
    requireNonBlank(command.resolutionComment(), "resolutionComment is required");

    Incident incident =
        incidentRepository
            .findById(command.incidentId())
            .orElseThrow(() -> new IncidentNotFoundException(command.incidentId()));

    Incident closed = incident.close();
    incidentRepository.update(closed);
    return closed;
  }

  private static void requireNonBlank(String value, String message) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(message);
    }
  }
}
