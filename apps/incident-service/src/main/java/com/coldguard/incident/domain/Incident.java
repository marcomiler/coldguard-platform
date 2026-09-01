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

    /**
     * @throws IncidentAlreadyClosedException if this incident is already
     *                                        {@link IncidentStatus#CLOSED}.
     */
    public Incident close() {
        if (status == IncidentStatus.CLOSED) {
            throw new IncidentAlreadyClosedException(id);
        }
        return new Incident(id, assetId, sensorId, anomalyType, impact, urgency, priority,
                IncidentStatus.CLOSED, createdAt);
    }
}
