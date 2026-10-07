package com.coldguard.incident.infrastructure;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

interface IncidentJpaRepository
    extends JpaRepository<IncidentEntity, UUID>, JpaSpecificationExecutor<IncidentEntity> {

  @Query(
      """
      select i from IncidentEntity i
       where i.assetId = :assetId and i.sensorId = :sensorId
         and i.anomalyType = :anomalyType and i.status <> com.coldguard.incident.domain.IncidentStatus.CLOSED
      """)
  Optional<IncidentEntity> findOpen(String assetId, String sensorId, String anomalyType);
}
