package com.coldguard.gateway.api.telemetry;

import java.time.Instant;

/** Connectivity is independent of the sensor's lifecycle status. */
public record ConnectivityResponse(
    String sensorId,
    String assetId,
    Instant lastReadingAt,
    long expectedReadingIntervalSeconds,
    Instant connectivityLostAt) {}
