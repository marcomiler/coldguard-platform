package com.coldguard.telemetry.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * What the evaluation remembers about a sensor between readings: the running streak of consecutive
 * out-of-range readings and whether it has stopped reporting. A local read model, not a copy of the
 * Asset sensor.
 *
 * <p>A condition is <em>persistent</em> when at least {@code minConsecutiveBreaches} eligible
 * out-of-range readings of the same anomaly type arrive one after another, each within the
 * persistence window counted from the first of the streak. An eligible reading within range ends
 * the streak.
 */
public record SensorCondition(
    UUID sensorId,
    UUID assetId,
    SensorStatus sensorStatus,
    int expectedIntervalSeconds,
    Instant lastReadingAt,
    Instant lastEvaluatedRecordedAt,
    AnomalyType breachAnomalyType,
    int breachStreak,
    Instant breachStreakStartedAt,
    Instant connectivityLostAt,
    long version) {

  /** The outcome of evaluating one reading. */
  public record Step(SensorCondition condition, boolean persistent, boolean late) {}

  /** The first reading of a sensor. */
  public static SensorCondition first(SensorContext context, Instant receivedAt) {
    return new SensorCondition(
        context.sensorId(),
        context.assetId(),
        context.status(),
        expectedSeconds(context),
        receivedAt,
        null,
        null,
        0,
        null,
        null,
        1);
  }

  /**
   * A reading of any kind has just arrived: refresh what Asset says and note that the sensor is
   * reporting again.
   */
  public SensorCondition seen(SensorContext context, Instant receivedAt) {
    return new SensorCondition(
        sensorId,
        context.assetId(),
        context.status(),
        context.profile() == null ? expectedIntervalSeconds : expectedSeconds(context),
        receivedAt,
        lastEvaluatedRecordedAt,
        breachAnomalyType,
        breachStreak,
        breachStreakStartedAt,
        null,
        version);
  }

  /** True when the sensor was marked as having lost connectivity and is reporting again. */
  public boolean reconnected() {
    return connectivityLostAt != null;
  }

  /**
   * Applies an eligible reading. A reading older than one already evaluated (late) changes nothing:
   * it cannot rewrite the streak, and it raises no event.
   */
  public Step evaluate(Instant recordedAt, Optional<Anomaly> anomaly, EvaluationProfile profile) {
    if (lastEvaluatedRecordedAt != null && !recordedAt.isAfter(lastEvaluatedRecordedAt)) {
      return new Step(this, false, true);
    }
    if (anomaly.isEmpty()) {
      return new Step(streak(null, 0, null, recordedAt), false, false);
    }
    AnomalyType type = anomaly.get().type();
    boolean continues =
        breachStreak > 0
            && type == breachAnomalyType
            && !recordedAt.isAfter(breachStreakStartedAt.plus(profile.persistenceWindow()));
    int streak = continues ? breachStreak + 1 : 1;
    Instant startedAt = continues ? breachStreakStartedAt : recordedAt;
    return new Step(
        streak(type, streak, startedAt, recordedAt),
        streak >= profile.minConsecutiveBreaches(),
        false);
  }

  private SensorCondition streak(
      AnomalyType type, int streak, Instant startedAt, Instant evaluatedAt) {
    return new SensorCondition(
        sensorId,
        assetId,
        sensorStatus,
        expectedIntervalSeconds,
        lastReadingAt,
        evaluatedAt,
        type,
        streak,
        startedAt,
        connectivityLostAt,
        version);
  }

  /** 0 means "unknown" (no profile): such a sensor has no interval to miss. */
  private static int expectedSeconds(SensorContext context) {
    return context.profile() == null ? 0 : (int) context.profile().expectedInterval().toSeconds();
  }
}
