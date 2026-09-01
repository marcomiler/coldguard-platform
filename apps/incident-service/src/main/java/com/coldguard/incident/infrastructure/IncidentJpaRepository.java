package com.coldguard.incident.infrastructure;

import com.coldguard.incident.domain.IncidentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface IncidentJpaRepository extends JpaRepository<IncidentEntity, UUID> {

    Optional<IncidentEntity> findFirstByAssetIdAndSensorIdAndAnomalyTypeAndStatus(
            String assetId, String sensorId, String anomalyType, IncidentStatus status);
}
