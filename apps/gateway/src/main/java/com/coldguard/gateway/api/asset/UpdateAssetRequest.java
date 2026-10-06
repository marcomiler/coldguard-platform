package com.coldguard.gateway.api.asset;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Partial update: a field that is absent or null stays as it is; a blank description clears it.
 * {@code expectedVersion} guards against a concurrent modification.
 */
public record UpdateAssetRequest(
    @NotNull Long expectedVersion,
    @Size(max = 120) String name,
    @Size(max = 500) String description,
    Criticality criticality) {}
