package com.coldguard.telemetry.application;

import java.time.Instant;

/** A reading as the producer sent it, before any validation. */
public record IncomingReading(
    String readingId, String sensorId, Instant recordedAt, double value, String unit) {}
