package com.coldguard.gateway.api.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(@NotBlank @Size(max = 120) String name) {}
