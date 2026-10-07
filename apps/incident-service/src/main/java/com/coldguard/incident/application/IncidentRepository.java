package com.coldguard.incident.application;

import com.coldguard.incident.domain.Incident;
import java.util.Optional;

public interface IncidentRepository {

  /**
   * @throws DuplicateIncidentException if a persistence-level uniqueness constraint rejects this
   *     incident (e.g. a concurrent request already opened one for the same asset/sensor/anomaly
   *     type).
   */
  void save(Incident incident);

  /** The open (not closed) incident for this asset, sensor and anomaly type, if any. */
  Optional<Incident> findOpen(String assetId, String sensorId, String anomalyType);

  Optional<Incident> findById(String incidentId);

  /**
   * @throws ConcurrentIncidentUpdateException if the stored incident changed since it was read
   */
  void update(Incident incident);

  /** Newest first. */
  PageResult<Incident> search(IncidentSearch search, PageQuery page);
}
