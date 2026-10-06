package com.coldguard.telemetry.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Persistence (RN-005 / RN-011): consecutive eligible out-of-range readings of one type, within a
 * window.
 */
class SensorConditionTest {

  private static final Instant T0 = Instant.parse("2026-10-06T12:00:00Z");
  private static final EvaluationProfile PROFILE =
      new EvaluationProfile(
          new BigDecimal("2"),
          new BigDecimal("8"),
          "CELSIUS",
          BigDecimal.ONE,
          new BigDecimal("3"),
          new BigDecimal("6"),
          3,
          Duration.ofMinutes(5),
          Duration.ofSeconds(5));
  private static final SensorContext CONTEXT =
      new SensorContext(
          UUID.randomUUID(), UUID.randomUUID(), Criticality.HIGH, SensorStatus.ACTIVE, PROFILE);

  private static Optional<Anomaly> above() {
    return Optional.of(
        new Anomaly(AnomalyType.TEMPERATURE_ABOVE_MAX, BigDecimal.ONE, MagnitudeLevel.MEDIUM));
  }

  private static Optional<Anomaly> below() {
    return Optional.of(
        new Anomaly(AnomalyType.TEMPERATURE_BELOW_MIN, BigDecimal.ONE, MagnitudeLevel.MEDIUM));
  }

  private final SensorCondition fresh = SensorCondition.first(CONTEXT, T0);

  private static SensorCondition.Step apply(
      SensorCondition condition, long secondsAfterT0, Optional<Anomaly> anomaly) {
    return condition.evaluate(T0.plusSeconds(secondsAfterT0), anomaly, PROFILE);
  }

  @Test
  void aFirstOutOfRangeReadingStartsAStreakThatIsNotPersistentYet() {
    var step = apply(fresh, 0, above());

    assertThat(step.condition().breachStreak()).isEqualTo(1);
    assertThat(step.condition().breachAnomalyType()).isEqualTo(AnomalyType.TEMPERATURE_ABOVE_MAX);
    assertThat(step.condition().breachStreakStartedAt()).isEqualTo(T0);
    assertThat(step.persistent()).isFalse();
    assertThat(step.late()).isFalse();
  }

  @Test
  void theConditionBecomesPersistentWithTheConfiguredNumberOfConsecutiveReadings() {
    var first = apply(fresh, 0, above());
    var second = apply(first.condition(), 5, above());
    var third = apply(second.condition(), 10, above());
    var fourth = apply(third.condition(), 15, above());

    assertThat(second.persistent()).isFalse();
    assertThat(third.persistent()).isTrue();
    assertThat(third.condition().breachStreak()).isEqualTo(3);
    assertThat(fourth.persistent()).isTrue();
    assertThat(fourth.condition().breachStreakStartedAt()).isEqualTo(T0);
  }

  @Test
  void anEligibleReadingWithinRangeEndsTheStreak() {
    var first = apply(fresh, 0, above());
    var second = apply(first.condition(), 5, above());
    var recovered = apply(second.condition(), 10, Optional.empty());
    var again = apply(recovered.condition(), 15, above());

    assertThat(recovered.condition().breachStreak()).isZero();
    assertThat(recovered.condition().breachAnomalyType()).isNull();
    assertThat(recovered.condition().breachStreakStartedAt()).isNull();
    assertThat(recovered.persistent()).isFalse();
    assertThat(again.condition().breachStreak()).as("a new streak starts at 1").isEqualTo(1);
  }

  @Test
  void aDifferentAnomalyTypeRestartsTheStreak() {
    var first = apply(fresh, 0, above());
    var second = apply(first.condition(), 5, above());
    var other = apply(second.condition(), 10, below());

    assertThat(other.condition().breachStreak()).isEqualTo(1);
    assertThat(other.condition().breachAnomalyType()).isEqualTo(AnomalyType.TEMPERATURE_BELOW_MIN);
    assertThat(other.condition().breachStreakStartedAt()).isEqualTo(T0.plusSeconds(10));
    assertThat(other.persistent()).isFalse();
  }

  @Test
  void aReadingOutsideTheWindowOfTheStreakRestartsIt() {
    var first = apply(fresh, 0, above());
    var second = apply(first.condition(), 100, above());
    var expired = apply(second.condition(), 301, above());

    assertThat(second.condition().breachStreak()).isEqualTo(2);
    assertThat(expired.condition().breachStreak()).isEqualTo(1);
    assertThat(expired.condition().breachStreakStartedAt()).isEqualTo(T0.plusSeconds(301));
    assertThat(expired.persistent()).isFalse();
  }

  @Test
  void aReadingExactlyAtTheEndOfTheWindowStillCounts() {
    var first = apply(fresh, 0, above());

    var atTheLimit = apply(first.condition(), 300, above());

    assertThat(atTheLimit.condition().breachStreak()).isEqualTo(2);
  }

  @Test
  void aLateReadingChangesNothingAndRaisesNoStreak() {
    var first = apply(fresh, 100, above());
    var second = apply(first.condition(), 110, above());

    var older = apply(second.condition(), 105, above());
    var sameInstant = apply(second.condition(), 110, above());

    assertThat(older.late()).isTrue();
    assertThat(older.condition()).isEqualTo(second.condition());
    assertThat(older.persistent()).isFalse();
    assertThat(sameInstant.late()).isTrue();
    assertThat(apply(second.condition(), 111, above()).late()).isFalse();
  }

  @Test
  void aLateInRangeReadingDoesNotEndTheStreakEither() {
    var first = apply(fresh, 100, above());

    var late = apply(first.condition(), 50, Optional.empty());

    assertThat(late.late()).isTrue();
    assertThat(late.condition().breachStreak()).isEqualTo(1);
  }

  @Test
  void seenRefreshesWhatAssetSaysAndClearsLostConnectivity() {
    SensorCondition lost =
        new SensorCondition(
            CONTEXT.sensorId(),
            UUID.randomUUID(),
            SensorStatus.INACTIVE,
            99,
            T0,
            null,
            null,
            0,
            null,
            T0,
            4);
    Instant now = T0.plusSeconds(60);

    SensorCondition seen = lost.seen(CONTEXT, now);

    assertThat(lost.reconnected()).isTrue();
    assertThat(seen.reconnected()).isFalse();
    assertThat(seen.connectivityLostAt()).isNull();
    assertThat(seen.lastReadingAt()).isEqualTo(now);
    assertThat(seen.assetId()).isEqualTo(CONTEXT.assetId());
    assertThat(seen.sensorStatus()).isEqualTo(SensorStatus.ACTIVE);
    assertThat(seen.expectedIntervalSeconds()).isEqualTo(5);
    assertThat(seen.version()).isEqualTo(4);
  }

  @Test
  void aSensorWithoutAProfileHasNoKnownIntervalAndKeepsTheLastOne() {
    SensorContext noProfile =
        new SensorContext(
            CONTEXT.sensorId(), CONTEXT.assetId(), Criticality.LOW, SensorStatus.ACTIVE, null);

    assertThat(SensorCondition.first(noProfile, T0).expectedIntervalSeconds()).isZero();
    assertThat(fresh.seen(noProfile, T0.plusSeconds(1)).expectedIntervalSeconds()).isEqualTo(5);
  }

  @Test
  void ineligibilityFollowsTheStatusThenTheProfile() {
    SensorContext maintenance =
        new SensorContext(
            UUID.randomUUID(),
            UUID.randomUUID(),
            Criticality.LOW,
            SensorStatus.IN_MAINTENANCE,
            PROFILE);
    SensorContext noProfile =
        new SensorContext(
            UUID.randomUUID(), UUID.randomUUID(), Criticality.LOW, SensorStatus.ACTIVE, null);
    SensorContext retiredNoProfile =
        new SensorContext(
            UUID.randomUUID(), UUID.randomUUID(), Criticality.LOW, SensorStatus.RETIRED, null);

    assertThat(CONTEXT.ineligibility()).isNull();
    assertThat(maintenance.ineligibility()).isEqualTo(IneligibilityReason.SENSOR_NOT_ACTIVE);
    assertThat(noProfile.ineligibility()).isEqualTo(IneligibilityReason.NO_PROFILE);
    assertThat(retiredNoProfile.ineligibility()).isEqualTo(IneligibilityReason.SENSOR_NOT_ACTIVE);
  }
}
