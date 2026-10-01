package com.coldguard.incident.infrastructure;

import com.coldguard.incident.domain.IncidentStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface IncidentJpaRepository extends JpaRepository<IncidentEntity, UUID> {

  Optional<IncidentEntity> findFirstByAssetIdAndSensorIdAndAnomalyTypeAndStatus(
      String assetId, String sensorId, String anomalyType, IncidentStatus status);
}
