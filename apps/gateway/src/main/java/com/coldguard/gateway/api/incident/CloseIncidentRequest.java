package com.coldguard.gateway.api.incident;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CloseIncidentRequest(
    @NotBlank @Size(max = 500) String cause,
    @NotBlank @Size(max = 2000) String resolutionComment) {}
