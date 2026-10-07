package com.coldguard.incident.application;

import com.coldguard.incident.domain.Criticality;
import com.coldguard.incident.domain.Magnitude;

/** An anomaly that may need a new incident; {@code sourceReadingId} is null for manual creation. */
public record OpenIncidentCommand(
    String assetId,
    Criticality assetCriticality,
    String sensorId,
    String anomalyType,
    Magnitude magnitude,
    boolean persistent,
    String sourceReadingId) {}
