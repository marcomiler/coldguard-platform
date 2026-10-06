package com.coldguard.gateway.api.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record RegisterAssetRequest(
    @NotBlank String siteId,
    @NotBlank @Size(max = 120) String name,
    @Size(max = 500) String description,
    @NotNull Criticality criticality) {}
