package com.coldguard.simulator.domain;

import java.time.Instant;
import java.util.UUID;

/**
 * A reading waiting to be delivered. Its id never changes, even across retries, so Telemetry can
 * deduplicate a redelivery.
 */
public record PendingReading(
    UUID readingId, String sensorId, Instant recordedAt, double value, String unit) {}
