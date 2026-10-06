package com.coldguard.gateway.api.asset;

import jakarta.validation.constraints.NotNull;

/** Deviation, in the profile's unit, from which an anomaly reaches each magnitude. */
public record MagnitudeBands(
    @NotNull Double mediumFrom, @NotNull Double highFrom, @NotNull Double criticalFrom) {}
