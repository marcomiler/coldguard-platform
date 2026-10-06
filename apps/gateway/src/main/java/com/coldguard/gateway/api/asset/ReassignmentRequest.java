package com.coldguard.gateway.api.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReassignmentRequest(
    @NotBlank String targetAssetId, @NotBlank @Size(max = 500) String reason) {}
