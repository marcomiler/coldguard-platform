package com.coldguard.gateway.api.asset;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

public record InitialCalibrationRequest(
    @NotNull CalibrationKind kind,
    @NotNull Instant performedAt,
    @NotBlank @Size(max = 500) String reason) {}
