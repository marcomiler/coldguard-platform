package com.coldguard.incident.application;

import com.coldguard.incident.domain.Impact;
import com.coldguard.incident.domain.Incident;
import com.coldguard.incident.domain.IncidentAlreadyOpenException;
import com.coldguard.incident.domain.IncidentStatus;
import com.coldguard.incident.domain.Priority;
import com.coldguard.incident.domain.Urgency;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class CreateIncidentService {

  private final IncidentRepository incidentRepository;

  public CreateIncidentService(IncidentRepository incidentRepository) {
    this.incidentRepository = incidentRepository;
  }

  public Incident create(CreateIncidentCommand command) {
    incidentRepository
        .findOpenIncidentId(command.assetId(), command.sensorId(), command.anomalyType())
        .ifPresent(
            existingId -> {
              throw new IncidentAlreadyOpenException(existingId);
            });

    Impact impact = PriorityCalculator.impactFrom(command.assetCriticality());
    Urgency urgency = PriorityCalculator.urgencyFrom(command.magnitude(), command.persistent());
    Priority priority = PriorityCalculator.priorityFrom(impact, urgency);

    Incident incident =
        new Incident(
            UUID.randomUUID().toString(),
            command.assetId(),
            command.sensorId(),
            command.anomalyType(),
            impact,
            urgency,
            priority,
            IncidentStatus.CREATED,
            Instant.now());

    try {
      incidentRepository.save(incident);
    } catch (DuplicateIncidentException ex) {
      // Lost a race against a concurrent request for the same asset/sensor/anomaly
      // type:
      // the uniqueness constraint is the final authority, re-read the winning
      // incident id.
      String existingId = findExistingId(command, ex);
      throw new IncidentAlreadyOpenException(existingId);
    }

    return incident;
  }

  private String findExistingId(CreateIncidentCommand command, DuplicateIncidentException cause) {
    Optional<String> existingId =
        incidentRepository.findOpenIncidentId(
            command.assetId(), command.sensorId(), command.anomalyType());
    if (existingId.isEmpty()) {
      throw cause;
    }
    return existingId.get();
  }
}
