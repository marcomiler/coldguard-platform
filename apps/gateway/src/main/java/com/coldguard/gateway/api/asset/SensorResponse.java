package com.coldguard.gateway.api.asset;

import java.time.Instant;

public record SensorResponse(
    String id,
    String serialNumber,
    String model,
    String measurementUnit,
    String assetId,
    SensorStatus status,
    Instant statusChangedAt,
    Instant lastCalibrationRecordedAt,
    Instant lastCalibrationValidUntil,
    Instant createdAt,
    Instant updatedAt,
    long version) {}
