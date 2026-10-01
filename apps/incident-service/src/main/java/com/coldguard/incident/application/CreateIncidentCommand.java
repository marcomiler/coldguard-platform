package com.coldguard.incident.application;

import com.coldguard.incident.domain.Criticality;
import com.coldguard.incident.domain.Magnitude;

public record CreateIncidentCommand(
    String assetId,
    Criticality assetCriticality,
    String sensorId,
    String anomalyType,
    Magnitude magnitude,
    boolean persistent,
    String correlationId) {}
