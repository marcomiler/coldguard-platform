package com.coldguard.gateway.api.asset;

import jakarta.validation.constraints.NotNull;

/** {@code minConsecutiveBreaches} out-of-range readings within {@code windowSeconds}. */
public record Persistence(
    @NotNull Integer minConsecutiveBreaches, @NotNull Integer windowSeconds) {}
