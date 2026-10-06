package com.coldguard.telemetry.domain;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Decides whether a reading is an anomaly and how serious it is. Pure: no state, no infrastructure.
 * The same rule applies to every source of readings.
 */
public final class ReadingEvaluator {

  private ReadingEvaluator() {}

  /** Empty when the reading is within the range (bounds included). */
  public static Optional<Anomaly> evaluate(BigDecimal value, EvaluationProfile profile) {
    if (value.compareTo(profile.maxTemperature()) > 0) {
      BigDecimal deviation = value.subtract(profile.maxTemperature());
      return Optional.of(
          new Anomaly(AnomalyType.TEMPERATURE_ABOVE_MAX, deviation, magnitude(deviation, profile)));
    }
    if (value.compareTo(profile.minTemperature()) < 0) {
      BigDecimal deviation = profile.minTemperature().subtract(value);
      return Optional.of(
          new Anomaly(AnomalyType.TEMPERATURE_BELOW_MIN, deviation, magnitude(deviation, profile)));
    }
    return Optional.empty();
  }

  /** A deviation that reaches a band's lower bound belongs to that band. */
  static MagnitudeLevel magnitude(BigDecimal deviation, EvaluationProfile profile) {
    if (deviation.compareTo(profile.mediumFrom()) < 0) {
      return MagnitudeLevel.LOW;
    }
    if (deviation.compareTo(profile.highFrom()) < 0) {
      return MagnitudeLevel.MEDIUM;
    }
    if (deviation.compareTo(profile.criticalFrom()) < 0) {
      return MagnitudeLevel.HIGH;
    }
    return MagnitudeLevel.CRITICAL;
  }
}
