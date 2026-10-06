package com.coldguard.asset.application;

import static com.coldguard.asset.application.ServiceFixture.ADMIN;
import static com.coldguard.asset.application.ServiceFixture.NOW;
import static com.coldguard.asset.application.ServiceFixture.OPERATOR;
import static com.coldguard.asset.application.ServiceFixture.SUPERVISOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.asset.application.SensorService.InitialCalibration;
import com.coldguard.asset.domain.Asset;
import com.coldguard.asset.domain.BusinessRuleViolationException;
import com.coldguard.asset.domain.CalibrationKind;
import com.coldguard.asset.domain.CalibrationRecord;
import com.coldguard.asset.domain.ResourceNotFoundException;
import com.coldguard.asset.domain.Sensor;
import com.coldguard.asset.domain.SensorStatus;
import com.coldguard.asset.support.EventContract;
import com.coldguard.commons.security.Actor;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SensorLifecycleServiceTest {

  private static final Duration DEFAULT_VALIDITY = Duration.ofDays(90);

  private final ServiceFixture f = new ServiceFixture();
  private final SensorService sensors = f.sensors(DEFAULT_VALIDITY);
  private final SensorLifecycleService lifecycle = f.lifecycle(DEFAULT_VALIDITY);
  private final Asset asset = f.anAsset();

  private Sensor aSensor() {
    return sensors.register(
        ADMIN, asset.id(), "SN-" + UUID.randomUUID(), null, "CELSIUS", null, null);
  }

  private Sensor stored(Sensor sensor) {
    return f.store.sensors.get(sensor.id());
  }

  private static String code(Throwable e) {
    return ((BusinessRuleViolationException) e).code();
  }

  @Test
  void everyOperationRequiresThePlatformAdminRole() {
    Sensor sensor = aSensor();
    for (Actor caller : new Actor[] {null, SUPERVISOR, OPERATOR, Actor.system("job")}) {
      assertThatThrownBy(
              () -> lifecycle.changeStatus(caller, sensor.id(), SensorStatus.INACTIVE, "x"))
          .isInstanceOf(AssetAccessDeniedException.class);
      assertThatThrownBy(() -> lifecycle.retire(caller, sensor.id(), "x"))
          .isInstanceOf(AssetAccessDeniedException.class);
      assertThatThrownBy(
              () ->
                  lifecycle.recordCalibration(
                      caller, sensor.id(), CalibrationKind.CALIBRATION, NOW.minusSeconds(1), "x"))
          .isInstanceOf(AssetAccessDeniedException.class);
      assertThatThrownBy(() -> lifecycle.reassign(caller, sensor.id(), asset.id(), "x"))
          .isInstanceOf(AssetAccessDeniedException.class);
    }
    assertThat(stored(sensor).status()).isEqualTo(SensorStatus.ACTIVE);
  }

  // ---- changeStatus ------------------------------------------------------------------------

  @Test
  void changeStatus_persistsAuditsAndPublishesWithWhoWhenWhyAndBeforeAfter() {
    Sensor sensor = aSensor();
    f.events.published.clear();
    f.store.history.clear();
    f.clock.advance(Duration.ofMinutes(5));

    Sensor moved =
        lifecycle.changeStatus(
            ADMIN, sensor.id(), SensorStatus.IN_MAINTENANCE, "  drifting readings ");

    assertThat(moved.status()).isEqualTo(SensorStatus.IN_MAINTENANCE);
    assertThat(moved.statusChangedAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
    assertThat(moved.version()).isEqualTo(2);
    assertThat(stored(sensor).status()).isEqualTo(SensorStatus.IN_MAINTENANCE);
    assertThat(f.store.history)
        .singleElement()
        .satisfies(
            h -> {
              assertThat(h.action()).isEqualTo("STATUS_CHANGED");
              assertThat(h.previousValue()).containsEntry("status", "ACTIVE");
              assertThat(h.newValue()).containsEntry("status", "IN_MAINTENANCE");
              assertThat(h.reason()).isEqualTo("drifting readings");
              assertThat(h.actorType()).isEqualTo("USER");
              assertThat(h.actorId()).isEqualTo("admin-1");
              assertThat(h.occurredAt()).isEqualTo(NOW.plus(Duration.ofMinutes(5)));
            });
    var event = f.events.only();
    assertThat(event.eventType()).isEqualTo("SensorStatusChanged");
    assertThat(event.routingKey()).isEqualTo("asset.sensor-status-changed");
    assertThat(event.aggregateType()).isEqualTo("Sensor");
    EventContract.assertConforms(event);
  }

  @Test
  void changeStatus_aReasonIsRequiredAndNothingChangesWithoutIt() {
    Sensor sensor = aSensor();
    f.events.published.clear();

    for (String reason : new String[] {null, "", "   "}) {
      assertThatThrownBy(
              () -> lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.INACTIVE, reason))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("reason");
    }
    assertThatThrownBy(
            () ->
                lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.INACTIVE, "x".repeat(501)))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(stored(sensor).status()).isEqualTo(SensorStatus.ACTIVE);
    assertThat(f.events.published).isEmpty();
  }

  @Test
  void changeStatus_toTheSameStatusIsRejectedWithoutAnyTrace() {
    Sensor sensor = aSensor();
    f.events.published.clear();
    int history = f.store.history.size();

    assertThatThrownBy(
            () -> lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.ACTIVE, "again"))
        .isInstanceOfSatisfying(
            BusinessRuleViolationException.class,
            e -> assertThat(e.code()).isEqualTo("SENSOR_TRANSITION_NOT_ALLOWED"));

    assertThat(stored(sensor).version()).isEqualTo(1);
    assertThat(f.events.published).isEmpty();
    assertThat(f.store.history).hasSize(history);
  }

  @Test
  void changeStatus_unknownSensorIsNotFound() {
    assertThatThrownBy(
            () -> lifecycle.changeStatus(ADMIN, UUID.randomUUID(), SensorStatus.INACTIVE, "x"))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  // ---- calibration: it never reactivates by itself -----------------------------------------

  @Test
  void leavingMaintenanceNeedsACalibrationRecordedAfterEnteringItAndAnExplicitChange() {
    Sensor sensor = aSensor();
    f.clock.advance(Duration.ofMinutes(1));
    lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.IN_MAINTENANCE, "drift");

    assertThatThrownBy(
            () -> lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.ACTIVE, "fixed"))
        .isInstanceOfSatisfying(
            BusinessRuleViolationException.class,
            e -> assertThat(e.code()).isEqualTo("CALIBRATION_EVIDENCE_REQUIRED"));

    f.clock.advance(Duration.ofMinutes(10));
    CalibrationRecord record =
        lifecycle.recordCalibration(
            ADMIN,
            sensor.id(),
            CalibrationKind.CALIBRATION,
            f.clock.instant().minusSeconds(30),
            "Recalibrated");

    assertThat(stored(sensor).status())
        .as("recording a calibration does not reactivate the sensor")
        .isEqualTo(SensorStatus.IN_MAINTENANCE);
    assertThat(record.validUntil()).isEqualTo(record.performedAt().plus(DEFAULT_VALIDITY));

    f.clock.advance(Duration.ofMinutes(1));
    Sensor active =
        lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.ACTIVE, "back in service");
    assertThat(active.status()).isEqualTo(SensorStatus.ACTIVE);
  }

  @Test
  void aCalibrationRecordedBeforeEnteringMaintenanceDoesNotCount() {
    Asset other = f.anAsset();
    Sensor sensor =
        sensors.register(
            ADMIN,
            other.id(),
            "SN-CAL",
            null,
            "CELSIUS",
            new InitialCalibration(CalibrationKind.CALIBRATION, NOW.minusSeconds(60), "Factory"),
            null);
    f.clock.advance(Duration.ofHours(1));
    lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.IN_MAINTENANCE, "inspection");

    assertThatThrownBy(
            () -> lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.ACTIVE, "done"))
        .isInstanceOfSatisfying(
            BusinessRuleViolationException.class,
            e -> assertThat(e.code()).isEqualTo("CALIBRATION_EVIDENCE_REQUIRED"));
  }

  @Test
  void leavingInactive_aStillValidCalibrationIsEnoughAnExpiredOneIsNot() {
    Sensor sensor =
        sensors.register(
            ADMIN,
            f.anAsset().id(),
            "SN-INACTIVE",
            null,
            "CELSIUS",
            new InitialCalibration(CalibrationKind.VERIFICATION, NOW.minusSeconds(60), "Check"),
            null);
    f.clock.advance(Duration.ofDays(1));
    lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.INACTIVE, "out of season");

    f.clock.advance(Duration.ofDays(1));
    assertThat(
            lifecycle
                .changeStatus(ADMIN, sensor.id(), SensorStatus.ACTIVE, "season starts")
                .status())
        .isEqualTo(SensorStatus.ACTIVE);

    lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.INACTIVE, "out of season again");
    f.clock.advance(DEFAULT_VALIDITY);
    assertThatThrownBy(
            () -> lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.ACTIVE, "too late"))
        .isInstanceOfSatisfying(
            BusinessRuleViolationException.class,
            e -> assertThat(e.code()).isEqualTo("CALIBRATION_EXPIRED"));
    assertThat(stored(sensor).status()).isEqualTo(SensorStatus.INACTIVE);
  }

  @Test
  void recordCalibration_storesDerivesTheExpiryAndPublishes() {
    Sensor sensor = aSensor();
    f.events.published.clear();
    f.store.history.clear();
    var performedAt = NOW.minusSeconds(600);

    CalibrationRecord record =
        lifecycle.recordCalibration(
            ADMIN, sensor.id(), CalibrationKind.VERIFICATION, performedAt, "On site check");

    assertThat(record.validUntil()).isEqualTo(performedAt.plus(DEFAULT_VALIDITY));
    assertThat(record.recordedAt()).isEqualTo(NOW);
    assertThat(record.recordedBy()).isEqualTo("admin-1");
    assertThat(f.store.calibrations).containsExactly(record);
    assertThat(stored(sensor).lastCalibrationRecordedAt()).isEqualTo(NOW);
    assertThat(stored(sensor).lastCalibrationValidUntil()).isEqualTo(record.validUntil());
    assertThat(stored(sensor).status()).isEqualTo(SensorStatus.ACTIVE);
    assertThat(f.store.history)
        .singleElement()
        .satisfies(
            h -> {
              assertThat(h.action()).isEqualTo("CALIBRATION_RECORDED");
              assertThat(h.previousValue()).isNull();
              assertThat(h.newValue()).containsEntry("kind", "VERIFICATION");
              assertThat(h.reason()).isEqualTo("On site check");
            });
    var event = f.events.only();
    assertThat(event.eventType()).isEqualTo("SensorCalibrationRecorded");
    EventContract.assertConforms(event);
  }

  @Test
  void recordCalibration_aSecondOneRecordsTheFirstAsThePreviousValue() {
    Sensor sensor = aSensor();
    lifecycle.recordCalibration(
        ADMIN, sensor.id(), CalibrationKind.CALIBRATION, NOW.minusSeconds(60), "First");
    f.store.history.clear();
    f.clock.advance(Duration.ofDays(1));

    lifecycle.recordCalibration(
        ADMIN,
        sensor.id(),
        CalibrationKind.CALIBRATION,
        f.clock.instant().minusSeconds(60),
        "Second");

    assertThat(f.store.history)
        .singleElement()
        .satisfies(h -> assertThat(h.previousValue()).containsKeys("recordedAt", "validUntil"));
    assertThat(f.store.calibrations).hasSize(2);
  }

  @Test
  void recordCalibration_validityComesFromTheProfileWhenItHasOne() {
    Sensor sensor = aSensor();
    f.profiles.upsert(ADMIN, ServiceFixture.draft(sensor.id(), "CELSIUS", Duration.ofHours(6)), 0);

    CalibrationRecord record =
        lifecycle.recordCalibration(
            ADMIN, sensor.id(), CalibrationKind.CALIBRATION, NOW.minusSeconds(60), "x");

    assertThat(record.validUntil()).isEqualTo(NOW.minusSeconds(60).plus(Duration.ofHours(6)));
  }

  @Test
  void recordCalibration_withoutAnyValidityIsRefusedAndStoresNothing() {
    Sensor sensor = aSensor();
    SensorLifecycleService noDefault = f.lifecycle(null);

    assertThatThrownBy(
            () ->
                noDefault.recordCalibration(
                    ADMIN, sensor.id(), CalibrationKind.CALIBRATION, NOW.minusSeconds(1), "x"))
        .isInstanceOf(CalibrationValidityNotConfiguredException.class);
    assertThat(f.store.calibrations).isEmpty();
    assertThat(stored(sensor).lastCalibrationRecordedAt()).isNull();
  }

  @Test
  void recordCalibration_validatesItsInput() {
    Sensor sensor = aSensor();

    assertThatThrownBy(
            () ->
                lifecycle.recordCalibration(
                    ADMIN, sensor.id(), CalibrationKind.CALIBRATION, NOW.plusSeconds(60), "x"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("future");
    assertThatThrownBy(
            () -> lifecycle.recordCalibration(ADMIN, sensor.id(), null, NOW.minusSeconds(1), "x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                lifecycle.recordCalibration(
                    ADMIN, sensor.id(), CalibrationKind.CALIBRATION, null, "x"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                lifecycle.recordCalibration(
                    ADMIN, sensor.id(), CalibrationKind.CALIBRATION, NOW.minusSeconds(1), " "))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reason");
    assertThat(f.store.calibrations).isEmpty();
  }

  // ---- reassignment ------------------------------------------------------------------------

  @Test
  void reassign_isRejectedOutsideMaintenance() {
    Sensor sensor = aSensor();
    Asset target = f.anAsset();

    assertThatThrownBy(() -> lifecycle.reassign(ADMIN, sensor.id(), target.id(), "moving"))
        .isInstanceOfSatisfying(
            BusinessRuleViolationException.class,
            e -> assertThat(e.code()).isEqualTo("REASSIGNMENT_NOT_ALLOWED"));
    assertThat(stored(sensor).assetId()).isEqualTo(asset.id());
    assertThat(f.store.assignments).hasSize(1);
  }

  @Test
  void reassign_inMaintenanceMovesItKeepsTheStatusAndKeepsThePreviousAssociation() {
    Sensor sensor = aSensor();
    Asset target = f.anAsset();
    f.clock.advance(Duration.ofMinutes(1));
    lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.IN_MAINTENANCE, "relocation");
    f.events.published.clear();
    f.store.history.clear();

    Sensor moved = lifecycle.reassign(ADMIN, sensor.id(), target.id(), " new cold room ");

    assertThat(moved.assetId()).isEqualTo(target.id());
    assertThat(moved.status()).isEqualTo(SensorStatus.IN_MAINTENANCE);
    assertThat(f.store.assignments).hasSize(2);
    assertThat(f.store.assignments.get(0).assetId()).isEqualTo(asset.id());
    assertThat(f.store.assignments.get(1).previousAssetId()).isEqualTo(asset.id());
    assertThat(f.store.assignments.get(1).assetId()).isEqualTo(target.id());
    assertThat(f.store.assignments.get(1).reason()).isEqualTo("new cold room");
    assertThat(f.store.history)
        .singleElement()
        .satisfies(
            h -> {
              assertThat(h.action()).isEqualTo("REASSIGNED");
              assertThat(h.previousValue()).containsEntry("assetId", asset.id().toString());
              assertThat(h.newValue()).containsEntry("assetId", target.id().toString());
            });
    var event = f.events.only();
    assertThat(event.eventType()).isEqualTo("SensorReassigned");
    assertThat(event.routingKey()).isEqualTo("asset.sensor-reassigned");
    EventContract.assertConforms(event);
  }

  @Test
  void reassign_toAnUnknownOrTheSameAssetIsRejected() {
    Sensor sensor = aSensor();
    f.clock.advance(Duration.ofMinutes(1));
    lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.IN_MAINTENANCE, "x");

    assertThatThrownBy(() -> lifecycle.reassign(ADMIN, sensor.id(), UUID.randomUUID(), "x"))
        .isInstanceOfSatisfying(
            ResourceNotFoundException.class,
            e -> assertThat(e.code()).isEqualTo("ASSET_NOT_FOUND"));
    assertThatThrownBy(() -> lifecycle.reassign(ADMIN, sensor.id(), asset.id(), "x"))
        .isInstanceOf(BusinessRuleViolationException.class);
    assertThatThrownBy(() -> lifecycle.reassign(ADMIN, sensor.id(), f.anAsset().id(), " "))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ---- retirement --------------------------------------------------------------------------

  @Test
  void retire_isTerminalPublishesBothEventsAndKeepsTheHistory() {
    Sensor sensor =
        sensors.register(
            ADMIN,
            f.anAsset().id(),
            "SN-RETIRE",
            null,
            "CELSIUS",
            new InitialCalibration(CalibrationKind.CALIBRATION, NOW.minusSeconds(60), "Factory"),
            null);
    f.events.published.clear();
    f.store.history.clear();
    f.clock.advance(Duration.ofMinutes(1));

    Sensor retired = lifecycle.retire(ADMIN, sensor.id(), "end of life");

    assertThat(retired.status()).isEqualTo(SensorStatus.RETIRED);
    assertThat(f.events.published.stream().map(e -> e.eventType()))
        .containsExactly("SensorRetired", "SensorStatusChanged");
    f.events.published.forEach(EventContract::assertConforms);
    assertThat(f.store.history)
        .singleElement()
        .satisfies(
            h -> {
              assertThat(h.action()).isEqualTo("RETIRED");
              assertThat(h.previousValue()).containsEntry("status", "ACTIVE");
              assertThat(h.newValue()).containsEntry("status", "RETIRED");
              assertThat(h.reason()).isEqualTo("end of life");
            });
    assertThat(f.store.sensors).containsKey(sensor.id());
    assertThat(f.store.calibrations).hasSize(1);
    assertThat(f.store.assignments).hasSize(1);
  }

  @Test
  void retire_nothingCanBeDoneToARetiredSensor() {
    Sensor sensor = aSensor();
    lifecycle.retire(ADMIN, sensor.id(), "end of life");
    Asset target = f.anAsset();

    assertThatThrownBy(
            () -> lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.ACTIVE, "revive"))
        .satisfies(e -> assertThat(code(e)).isEqualTo("SENSOR_TRANSITION_NOT_ALLOWED"));
    assertThatThrownBy(() -> lifecycle.retire(ADMIN, sensor.id(), "again"))
        .satisfies(e -> assertThat(code(e)).isEqualTo("SENSOR_TRANSITION_NOT_ALLOWED"));
    assertThatThrownBy(
            () ->
                lifecycle.recordCalibration(
                    ADMIN,
                    sensor.id(),
                    CalibrationKind.CALIBRATION,
                    f.clock.instant().minusSeconds(1),
                    "x"))
        .satisfies(e -> assertThat(code(e)).isEqualTo("SENSOR_TRANSITION_NOT_ALLOWED"));
    assertThatThrownBy(() -> lifecycle.reassign(ADMIN, sensor.id(), target.id(), "x"))
        .satisfies(e -> assertThat(code(e)).isEqualTo("REASSIGNMENT_NOT_ALLOWED"));
    assertThat(stored(sensor).status()).isEqualTo(SensorStatus.RETIRED);
  }

  @Test
  void changeStatusToRetiredIsTheSameAsRetire() {
    Sensor sensor = aSensor();
    f.events.published.clear();

    Sensor retired = lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.RETIRED, "scrapped");

    assertThat(retired.status()).isEqualTo(SensorStatus.RETIRED);
    assertThat(f.events.published.stream().map(e -> e.eventType()))
        .containsExactly("SensorRetired", "SensorStatusChanged");
    assertThat(f.store.history.get(f.store.history.size() - 1).action()).isEqualTo("RETIRED");
  }
}
