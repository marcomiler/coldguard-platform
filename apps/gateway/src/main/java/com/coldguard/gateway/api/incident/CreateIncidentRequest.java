package com.coldguard.gateway.api.incident;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Technical, manual creation of an incident; real incidents are created from telemetry. */
public record CreateIncidentRequest(
    @NotBlank @Size(max = 100) String assetId,
    @NotNull Criticality assetCriticality,
    @NotBlank @Size(max = 100) String sensorId,
    @NotBlank @Size(max = 100) String anomalyType,
    @NotNull Magnitude magnitude,
    Boolean persistent) {}
