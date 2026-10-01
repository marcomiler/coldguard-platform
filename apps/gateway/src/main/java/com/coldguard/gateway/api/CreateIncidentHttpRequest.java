package com.coldguard.gateway.api;

import com.coldguard.incident.grpc.v1.Criticality;
import com.coldguard.incident.grpc.v1.Magnitude;

public record CreateIncidentHttpRequest(
    String assetId,
    Criticality assetCriticality,
    String sensorId,
    String anomalyType,
    Magnitude magnitude,
    boolean persistent,
    String correlationId) {}
