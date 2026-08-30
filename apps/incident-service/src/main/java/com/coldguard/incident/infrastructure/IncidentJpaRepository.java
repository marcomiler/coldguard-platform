package com.coldguard.incident.infrastructure;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

interface IncidentJpaRepository extends JpaRepository<IncidentEntity, UUID> {

    Optional<IncidentEntity> findFirstByAssetIdAndSensorIdAndAnomalyType(
            String assetId, String sensorId, String anomalyType);
}
