package com.coldguard.incident.infrastructure;

import com.coldguard.incident.application.DuplicateIncidentException;
import com.coldguard.incident.application.IncidentRepository;
import com.coldguard.incident.domain.Incident;
import com.coldguard.incident.domain.IncidentStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

@Repository
public class IncidentRepositoryAdapter implements IncidentRepository {

  private final IncidentJpaRepository jpaRepository;

  public IncidentRepositoryAdapter(IncidentJpaRepository jpaRepository) {
    this.jpaRepository = jpaRepository;
  }

  @Override
  public void save(Incident incident) {
    IncidentEntity entity =
        new IncidentEntity(
            UUID.fromString(incident.id()),
            incident.assetId(),
            incident.sensorId(),
            incident.anomalyType(),
            incident.impact(),
            incident.urgency(),
            incident.priority(),
            incident.status(),
            incident.createdAt());
    try {
      jpaRepository.saveAndFlush(entity);
    } catch (DataIntegrityViolationException ex) {
      throw new DuplicateIncidentException(
          "An open incident already exists for this asset/sensor/anomaly type", ex);
    }
  }

  @Override
  public Optional<String> findOpenIncidentId(String assetId, String sensorId, String anomalyType) {
    return jpaRepository
        .findFirstByAssetIdAndSensorIdAndAnomalyTypeAndStatus(
            assetId, sensorId, anomalyType, IncidentStatus.CREATED)
        .map(entity -> entity.getId().toString());
  }

  @Override
  public Optional<Incident> findById(String incidentId) {
    UUID id;
    try {
      id = UUID.fromString(incidentId);
    } catch (IllegalArgumentException notAnId) {
      // Every stored id is a UUID, so anything else cannot exist; it is not a server error.
      return Optional.empty();
    }
    return jpaRepository.findById(id).map(this::toDomain);
  }

  @Override
  public void update(Incident incident) {
    IncidentEntity entity =
        new IncidentEntity(
            UUID.fromString(incident.id()),
            incident.assetId(),
            incident.sensorId(),
            incident.anomalyType(),
            incident.impact(),
            incident.urgency(),
            incident.priority(),
            incident.status(),
            incident.createdAt());
    jpaRepository.saveAndFlush(entity);
  }

  private Incident toDomain(IncidentEntity entity) {
    return new Incident(
        entity.getId().toString(),
        entity.getAssetId(),
        entity.getSensorId(),
        entity.getAnomalyType(),
        entity.getImpact(),
        entity.getUrgency(),
        entity.getPriority(),
        entity.getStatus(),
        entity.getCreatedAt());
  }
}
