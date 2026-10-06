package com.coldguard.asset.application;

import com.coldguard.asset.domain.Criticality;
import com.coldguard.asset.domain.OperationalProfile;
import com.coldguard.asset.domain.SensorStatus;
import java.util.UUID;

/**
 * What Telemetry needs to evaluate a sensor's readings: its asset and criticality, its status and
 * its profile. {@code profile} is null when none was set, which makes its readings not evaluable.
 */
public record SensorEvaluationContext(
    UUID sensorId,
    UUID assetId,
    Criticality assetCriticality,
    SensorStatus status,
    OperationalProfile profile) {}
