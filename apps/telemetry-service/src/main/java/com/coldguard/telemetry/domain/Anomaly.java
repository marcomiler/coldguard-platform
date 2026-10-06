package com.coldguard.telemetry.domain;

import java.math.BigDecimal;

/** A reading outside the range, how far outside, and how serious that is. */
public record Anomaly(AnomalyType type, BigDecimal deviation, MagnitudeLevel magnitude) {}
