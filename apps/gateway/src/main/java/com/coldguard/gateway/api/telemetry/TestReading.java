package com.coldguard.gateway.api.telemetry;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * One reading to inject. {@code readingId} (a UUID that makes the injection idempotent) is
 * generated when absent; {@code recordedAt} defaults to the moment of the request.
 */
public record TestReading(
    String readingId,
    @NotBlank String sensorId,
    Instant recordedAt,
    @NotNull Double value,
    @NotBlank @Size(max = 20) String unit) {}
