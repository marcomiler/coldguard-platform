package com.coldguard.incident.infrastructure;

import com.coldguard.incident.application.DuplicateIncidentException;
import com.coldguard.incident.application.IncidentRepository;
import com.coldguard.incident.domain.Incident;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public class IncidentRepositoryAdapter implements IncidentRepository {

    private final IncidentJpaRepository jpaRepository;

    public IncidentRepositoryAdapter(IncidentJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public void save(Incident incident) {
        IncidentEntity entity = new IncidentEntity(
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
        return jpaRepository.findFirstByAssetIdAndSensorIdAndAnomalyType(assetId, sensorId, anomalyType)
                .map(entity -> entity.getId().toString());
    }
}
