package com.coldguard.incident.application;

import com.coldguard.incident.domain.Incident;

import java.util.Optional;

/**
 * Persistence port for incidents. Implemented by an infrastructure adapter; this interface has
 * no dependency on any persistence technology.
 */
public interface IncidentRepository {

    /**
     * @throws DuplicateIncidentException if a persistence-level uniqueness constraint rejects
     *         this incident (e.g. a concurrent request already created one for the same
     *         asset/sensor/anomaly type).
     */
    void save(Incident incident);

    Optional<String> findOpenIncidentId(String assetId, String sensorId, String anomalyType);
}
