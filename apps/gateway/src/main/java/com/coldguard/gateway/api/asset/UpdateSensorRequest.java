package com.coldguard.gateway.api.asset;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Technical data only; absent or null fields stay as they are. */
public record UpdateSensorRequest(
    @NotNull Long expectedVersion,
    @Size(max = 80) String serialNumber,
    @Size(max = 80) String model) {}
