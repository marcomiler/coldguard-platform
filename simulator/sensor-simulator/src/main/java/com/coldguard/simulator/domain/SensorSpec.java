package com.coldguard.simulator.domain;

import java.time.Duration;

/**
 * What one simulated sensor does. Values are demonstration placeholders and must agree with the
 * profile the seed created for that sensor: the profile is the only source of thresholds.
 */
public record SensorSpec(
    String sensorId,
    String unit,
    Duration interval,
    double baseline,
    double noise,
    Scenario scenario,
    Double breachValue,
    Duration startAfter,
    int breachReadings,
    Duration silenceDuration) {}
