package com.coldguard.incident.domain;

import java.time.Instant;

public record Incident(
        String id,
        String assetId,
        String sensorId,
        String anomalyType,
        Impact impact,
        Urgency urgency,
        Priority priority,
        IncidentStatus status,
        Instant createdAt) {
}
