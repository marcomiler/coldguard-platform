package com.coldguard.incident.application;

import com.coldguard.incident.domain.Incident;

import java.util.Optional;

public interface IncidentRepository {

    /**
     * @throws DuplicateIncidentException if a persistence-level uniqueness
     *                                    constraint rejects
     *                                    this incident (e.g. a concurrent request
     *                                    already created one for the same
     *                                    asset/sensor/anomaly type).
     */
    void save(Incident incident);

    Optional<String> findOpenIncidentId(String assetId, String sensorId, String anomalyType);

    Optional<Incident> findById(String incidentId);

    void update(Incident incident);
}
