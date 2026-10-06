package com.coldguard.telemetry.domain;

import java.math.BigDecimal;
import java.time.Duration;

/** The part of a sensor's operational profile that evaluating a reading needs. */
public record EvaluationProfile(
    BigDecimal minTemperature,
    BigDecimal maxTemperature,
    String unit,
    BigDecimal mediumFrom,
    BigDecimal highFrom,
    BigDecimal criticalFrom,
    int minConsecutiveBreaches,
    Duration persistenceWindow,
    Duration expectedInterval) {}
