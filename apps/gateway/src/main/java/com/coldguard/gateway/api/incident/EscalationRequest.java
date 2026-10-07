package com.coldguard.gateway.api.incident;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record EscalationRequest(@NotBlank @Size(max = 500) String reason) {}
