package com.coldguard.asset.domain;

import static com.coldguard.asset.domain.SensorStatus.ACTIVE;
import static com.coldguard.asset.domain.SensorStatus.INACTIVE;
import static com.coldguard.asset.domain.SensorStatus.IN_MAINTENANCE;
import static com.coldguard.asset.domain.SensorStatus.RETIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/** The sensor state machine: every (current, target) pair, and the evidence guards of ACTIVE. */
class SensorLifecycleRulesTest {

  private static final Instant NOW = Instant.parse("2026-10-05T12:00:00Z");
  private static final Instant ENTERED = NOW.minus(Duration.ofHours(2));

  /** A sensor in {@code status} whose calibration is recent (after entering) and still valid. */
  private static Sensor withEvidence(SensorStatus status) {
    return sensor(status, ENTERED.plusSeconds(60), NOW.plus(Duration.ofDays(30)));
  }

  private static Sensor sensor(SensorStatus status, Instant recordedAt, Instant validUntil) {
    return new Sensor(
        UUID.randomUUID(),
        "SN-1",
        null,
        "CELSIUS",
        UUID.randomUUID(),
        status,
        ENTERED,
        recordedAt,
        validUntil,
        ENTERED.minusSeconds(3600),
        ENTERED,
        4);
  }

  static Stream<Arguments> everyPair() {
    return Stream.of(
        // from ACTIVE
        Arguments.of(ACTIVE, ACTIVE, false),
        Arguments.of(ACTIVE, IN_MAINTENANCE, true),
        Arguments.of(ACTIVE, INACTIVE, true),
        Arguments.of(ACTIVE, RETIRED, true),
        // from IN_MAINTENANCE (with fresh, valid evidence recorded after entering)
        Arguments.of(IN_MAINTENANCE, ACTIVE, true),
        Arguments.of(IN_MAINTENANCE, IN_MAINTENANCE, false),
        Arguments.of(IN_MAINTENANCE, INACTIVE, true),
        Arguments.of(IN_MAINTENANCE, RETIRED, true),
        // from INACTIVE (with a valid calibration)
        Arguments.of(INACTIVE, ACTIVE, true),
        Arguments.of(INACTIVE, IN_MAINTENANCE, true),
        Arguments.of(INACTIVE, INACTIVE, false),
        Arguments.of(INACTIVE, RETIRED, true),
        // RETIRED is terminal: nothing leaves it
        Arguments.of(RETIRED, ACTIVE, false),
        Arguments.of(RETIRED, IN_MAINTENANCE, false),
        Arguments.of(RETIRED, INACTIVE, false),
        Arguments.of(RETIRED, RETIRED, false));
  }

  @ParameterizedTest(name = "{0} -> {1}: allowed={2}")
  @MethodSource("everyPair")
  void everyStatusPair_isAllowedOrRejectedAsTheStateMachineSays(
      SensorStatus from, SensorStatus to, boolean allowed) {
    Sensor sensor = withEvidence(from);

    if (allowed) {
      Sensor moved = sensor.changeStatus(to, NOW);
      assertThat(moved.status()).isEqualTo(to);
      assertThat(moved.statusChangedAt()).isEqualTo(NOW);
      assertThat(moved.updatedAt()).isEqualTo(NOW);
      assertThat(moved.lastCalibrationRecordedAt()).isEqualTo(sensor.lastCalibrationRecordedAt());
      assertThat(moved.lastCalibrationValidUntil()).isEqualTo(sensor.lastCalibrationValidUntil());
      assertThat(moved.assetId()).isEqualTo(sensor.assetId());
    } else {
      assertThatThrownBy(() -> sensor.changeStatus(to, NOW))
          .isInstanceOfSatisfying(
              SensorTransitionNotAllowedException.class,
              e -> assertThat(e.code()).isEqualTo("SENSOR_TRANSITION_NOT_ALLOWED"));
    }
  }

  @Test
  void leavingMaintenance_needsACalibrationRecordedAfterEnteringIt() {
    Instant valid = NOW.plus(Duration.ofDays(30));

    for (Instant recordedAt : new Instant[] {null, ENTERED.minusSeconds(1), ENTERED}) {
      Sensor sensor = sensor(IN_MAINTENANCE, recordedAt, recordedAt == null ? null : valid);
      assertThatThrownBy(() -> sensor.changeStatus(ACTIVE, NOW))
          .as("calibration recorded at %s", recordedAt)
          .isInstanceOfSatisfying(
              CalibrationEvidenceRequiredException.class,
              e -> assertThat(e.code()).isEqualTo("CALIBRATION_EVIDENCE_REQUIRED"));
    }
    assertThat(
            sensor(IN_MAINTENANCE, ENTERED.plusNanos(1), valid).changeStatus(ACTIVE, NOW).status())
        .isEqualTo(ACTIVE);
  }

  @Test
  void leavingMaintenance_theNewCalibrationMustStillBeValid() {
    Sensor sensor = sensor(IN_MAINTENANCE, ENTERED.plusSeconds(60), NOW);

    assertThatThrownBy(() -> sensor.changeStatus(ACTIVE, NOW))
        .isInstanceOfSatisfying(
            CalibrationExpiredException.class,
            e -> assertThat(e.code()).isEqualTo("CALIBRATION_EXPIRED"));
  }

  @Test
  void leavingInactive_needsAValidCalibrationButNotANewRecord() {
    Instant recordedBeforeEntering = ENTERED.minus(Duration.ofDays(10));

    Sensor valid = sensor(INACTIVE, recordedBeforeEntering, NOW.plusSeconds(1));
    assertThat(valid.changeStatus(ACTIVE, NOW).status()).isEqualTo(ACTIVE);

    for (Instant validUntil : new Instant[] {null, NOW, NOW.minusSeconds(1)}) {
      Sensor expired =
          sensor(INACTIVE, validUntil == null ? null : recordedBeforeEntering, validUntil);
      assertThatThrownBy(() -> expired.changeStatus(ACTIVE, NOW))
          .as("valid until %s", validUntil)
          .isInstanceOfSatisfying(
              CalibrationExpiredException.class,
              e -> assertThat(e.code()).isEqualTo("CALIBRATION_EXPIRED"));
    }
  }

  @Test
  void movingAwayFromActiveNeedsNoEvidenceEvenWithoutAnyCalibration() {
    Sensor bare = sensor(ACTIVE, null, null);

    assertThat(bare.changeStatus(IN_MAINTENANCE, NOW).status()).isEqualTo(IN_MAINTENANCE);
    assertThat(bare.changeStatus(INACTIVE, NOW).status()).isEqualTo(INACTIVE);
    assertThat(bare.changeStatus(RETIRED, NOW).status()).isEqualTo(RETIRED);
  }

  @Test
  void reassign_isOnlyAllowedInMaintenanceToAnotherAsset() {
    UUID other = UUID.randomUUID();
    for (SensorStatus status : new SensorStatus[] {ACTIVE, INACTIVE, RETIRED}) {
      Sensor sensor = withEvidence(status);
      assertThatThrownBy(() -> sensor.reassignTo(other, NOW))
          .as("in %s", status)
          .isInstanceOfSatisfying(
              ReassignmentNotAllowedException.class,
              e -> assertThat(e.code()).isEqualTo("REASSIGNMENT_NOT_ALLOWED"));
    }

    Sensor inMaintenance = withEvidence(IN_MAINTENANCE);
    assertThatThrownBy(() -> inMaintenance.reassignTo(inMaintenance.assetId(), NOW))
        .isInstanceOf(ReassignmentNotAllowedException.class);

    Sensor moved = inMaintenance.reassignTo(other, NOW);
    assertThat(moved.assetId()).isEqualTo(other);
    assertThat(moved.status()).isEqualTo(IN_MAINTENANCE);
    assertThat(moved.statusChangedAt()).isEqualTo(ENTERED);
    assertThat(moved.updatedAt()).isEqualTo(NOW);
  }

  @Test
  void aRetiredSensorsCalibrationHistoryIsFrozen() {
    assertThatThrownBy(() -> withEvidence(RETIRED).withCalibration(NOW, NOW.plusSeconds(60), NOW))
        .isInstanceOf(SensorTransitionNotAllowedException.class);
    assertThat(withEvidence(INACTIVE).withCalibration(NOW, NOW.plusSeconds(60), NOW).status())
        .isEqualTo(INACTIVE);
  }
}
