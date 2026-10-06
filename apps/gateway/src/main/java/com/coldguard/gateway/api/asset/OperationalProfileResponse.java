package com.coldguard.gateway.api.asset;

import java.time.Instant;

public record OperationalProfileResponse(
    String sensorId,
    double minTemperature,
    double maxTemperature,
    String unit,
    MagnitudeBands magnitudeBands,
    Persistence persistence,
    int expectedReadingIntervalSeconds,
    Long calibrationValiditySeconds,
    long version,
    Instant updatedAt) {}
