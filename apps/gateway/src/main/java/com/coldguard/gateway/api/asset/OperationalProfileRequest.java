package com.coldguard.gateway.api.asset;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Only the shape is checked here; the service enforces the invariants (ranges, ordering of the
 * bands, positive durations). {@code version} is the expected current version, absent or 0 when the
 * profile is created.
 */
public record OperationalProfileRequest(
    @NotNull Double minTemperature,
    @NotNull Double maxTemperature,
    @NotBlank @Size(max = 20) String unit,
    @NotNull @Valid MagnitudeBands magnitudeBands,
    @NotNull @Valid Persistence persistence,
    @NotNull Integer expectedReadingIntervalSeconds,
    Long calibrationValiditySeconds,
    Long version) {}
