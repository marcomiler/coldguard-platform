package com.coldguard.asset.application;

import static com.coldguard.asset.application.ServiceFixture.ADMIN;
import static com.coldguard.asset.application.ServiceFixture.NOW;
import static com.coldguard.asset.application.ServiceFixture.OPERATOR;
import static com.coldguard.asset.application.ServiceFixture.SUPERVISOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.asset.application.SensorService.InitialCalibration;
import com.coldguard.asset.domain.AlreadyExistsException;
import com.coldguard.asset.domain.Asset;
import com.coldguard.asset.domain.CalibrationKind;
import com.coldguard.asset.domain.ResourceNotFoundException;
import com.coldguard.asset.domain.Sensor;
import com.coldguard.asset.domain.SensorStatus;
import com.coldguard.asset.domain.StaleVersionException;
import com.coldguard.asset.support.EventContract;
import com.coldguard.commons.security.Actor;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SensorServiceTest {

  private static final Duration DEFAULT_VALIDITY = Duration.ofDays(90);

  private final ServiceFixture f = new ServiceFixture();
  private final SensorService sensors = f.sensors(DEFAULT_VALIDITY);

  private Sensor register(Asset asset, String serial) {
    return sensors.register(ADMIN, asset.id(), serial, "Model X", "CELSIUS", null, null);
  }

  @Test
  void register_createsAnActiveSensorWithItsAssignmentAndHistory() {
    Asset asset = f.anAsset();

    Sensor sensor = register(asset, "SN-1");

    assertThat(sensor.status()).isEqualTo(SensorStatus.ACTIVE);
    assertThat(sensor.assetId()).isEqualTo(asset.id());
    assertThat(sensor.lastCalibrationValidUntil()).isNull();
    assertThat(f.store.assignments)
        .singleElement()
        .satisfies(
            a -> {
              assertThat(a.sensorId()).isEqualTo(sensor.id());
              assertThat(a.assetId()).isEqualTo(asset.id());
              assertThat(a.previousAssetId()).isNull();
              assertThat(a.assignedBy()).isEqualTo("admin-1");
            });
    assertThat(f.store.history)
        .singleElement()
        .satisfies(
            h -> {
              assertThat(h.action()).isEqualTo("REGISTERED");
              assertThat(h.actorType()).isEqualTo("USER");
              assertThat(h.actorId()).isEqualTo("admin-1");
              assertThat(h.previousValue()).isNull();
              assertThat(h.newValue()).containsEntry("status", "ACTIVE");
              assertThat(h.occurredAt()).isEqualTo(NOW);
            });
  }

  @Test
  void register_publishesNothingWithoutCalibrationOrProfile() {
    f.events.published.clear();
    register(f.anAsset(), "SN-1");

    assertThat(f.events.published.stream().map(e -> e.eventType()))
        .containsExactly("AssetRegistered");
  }

  @Test
  void register_withAnInitialCalibrationStoresItWithADerivedExpiry() {
    Asset asset = f.anAsset();
    f.events.published.clear();
    var performedAt = NOW.minusSeconds(3600);

    Sensor sensor =
        sensors.register(
            ADMIN,
            asset.id(),
            "SN-1",
            null,
            "CELSIUS",
            new InitialCalibration(CalibrationKind.CALIBRATION, performedAt, "Factory calibration"),
            null);

    assertThat(sensor.status()).isEqualTo(SensorStatus.ACTIVE);
    assertThat(sensor.lastCalibrationRecordedAt()).isEqualTo(NOW);
    assertThat(sensor.lastCalibrationValidUntil()).isEqualTo(performedAt.plus(DEFAULT_VALIDITY));
    assertThat(f.store.calibrations)
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.validUntil()).isEqualTo(performedAt.plus(DEFAULT_VALIDITY));
              assertThat(c.recordedBy()).isEqualTo("admin-1");
            });
    var event = f.events.only();
    assertThat(event.eventType()).isEqualTo("SensorCalibrationRecorded");
    assertThat(event.routingKey()).isEqualTo("asset.sensor-calibration-recorded");
    assertThat(event.aggregateType()).isEqualTo("Sensor");
    EventContract.assertConforms(event);
    assertThat(f.store.history.stream().map(h -> h.action()))
        .containsExactly("REGISTERED", "CALIBRATION_RECORDED");
  }

  @Test
  void register_calibrationValidityComesFromTheProfileWhenItDefinesOne() {
    Asset asset = f.anAsset();
    var performedAt = NOW.minusSeconds(60);

    Sensor sensor =
        sensors.register(
            ADMIN,
            asset.id(),
            "SN-1",
            null,
            "CELSIUS",
            new InitialCalibration(CalibrationKind.VERIFICATION, performedAt, "On site check"),
            ServiceFixture.draft(UUID.randomUUID(), "CELSIUS", Duration.ofHours(1)));

    assertThat(sensor.lastCalibrationValidUntil()).isEqualTo(performedAt.plus(Duration.ofHours(1)));
  }

  @Test
  void register_withoutAnyValidityRefusesTheCalibrationInsteadOfInventingOne() {
    Asset asset = f.anAsset();
    SensorService noDefault = f.sensors(null);

    assertThatThrownBy(
            () ->
                noDefault.register(
                    ADMIN,
                    asset.id(),
                    "SN-1",
                    null,
                    "CELSIUS",
                    new InitialCalibration(CalibrationKind.CALIBRATION, NOW.minusSeconds(1), "x"),
                    null))
        .isInstanceOf(CalibrationValidityNotConfiguredException.class);
    assertThat(f.store.sensors).isEmpty();
    assertThat(f.store.calibrations).isEmpty();
  }

  @Test
  void register_aCalibrationInTheFutureIsInvalid() {
    Asset asset = f.anAsset();

    assertThatThrownBy(
            () ->
                sensors.register(
                    ADMIN,
                    asset.id(),
                    "SN-1",
                    null,
                    "CELSIUS",
                    new InitialCalibration(CalibrationKind.CALIBRATION, NOW.plusSeconds(60), "x"),
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("future");
    assertThat(f.store.sensors).isEmpty();
  }

  @Test
  void register_aCalibrationNeedsAReason() {
    Asset asset = f.anAsset();

    assertThatThrownBy(
            () ->
                sensors.register(
                    ADMIN,
                    asset.id(),
                    "SN-1",
                    null,
                    "CELSIUS",
                    new InitialCalibration(CalibrationKind.CALIBRATION, NOW.minusSeconds(1), " "),
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("reason");
  }

  @Test
  void register_withAProfileStoresItAndPublishesIt() {
    Asset asset = f.anAsset();
    f.events.published.clear();

    Sensor sensor =
        sensors.register(
            ADMIN,
            asset.id(),
            "SN-1",
            null,
            "CELSIUS",
            null,
            ServiceFixture.draft(UUID.randomUUID(), "CELSIUS", null));

    assertThat(f.store.profiles.get(sensor.id()).version()).isEqualTo(1);
    assertThat(f.events.only().eventType()).isEqualTo("OperationalProfileUpdated");
    assertThat(f.store.history.stream().map(h -> h.action()))
        .containsExactly("REGISTERED", "PROFILE_UPDATED");
  }

  @Test
  void register_aProfileInAnotherUnitIsInvalid() {
    Asset asset = f.anAsset();

    assertThatThrownBy(
            () ->
                sensors.register(
                    ADMIN,
                    asset.id(),
                    "SN-1",
                    null,
                    "CELSIUS",
                    null,
                    ServiceFixture.draft(UUID.randomUUID(), "FAHRENHEIT", null)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("unit");
  }

  @Test
  void register_rejectsUnknownAssetsAndDuplicateSerials() {
    assertThatThrownBy(
            () -> sensors.register(ADMIN, UUID.randomUUID(), "SN-1", null, "CELSIUS", null, null))
        .isInstanceOf(ResourceNotFoundException.class)
        .extracting(e -> ((ResourceNotFoundException) e).code())
        .isEqualTo("ASSET_NOT_FOUND");

    Asset asset = f.anAsset();
    register(asset, "SN-1");
    assertThatThrownBy(() -> register(asset, "sn-1"))
        .isInstanceOf(AlreadyExistsException.class)
        .extracting(e -> ((AlreadyExistsException) e).code())
        .isEqualTo("SENSOR_SERIAL_DUPLICATED");
  }

  @Test
  void commandsRequireAdminAndQueriesAdminOrSupervisor() {
    Asset asset = f.anAsset();
    Sensor sensor = register(asset, "SN-1");

    for (Actor caller : new Actor[] {null, SUPERVISOR, OPERATOR}) {
      assertThatThrownBy(
              () -> sensors.register(caller, asset.id(), "SN-2", null, "CELSIUS", null, null))
          .isInstanceOf(AssetAccessDeniedException.class);
      assertThatThrownBy(() -> sensors.update(caller, sensor.id(), 1, "SN-9", null))
          .isInstanceOf(AssetAccessDeniedException.class);
    }
    assertThat(sensors.get(SUPERVISOR, sensor.id())).isEqualTo(sensor);
    assertThatThrownBy(() -> sensors.get(OPERATOR, sensor.id()))
        .isInstanceOf(AssetAccessDeniedException.class);
    assertThatThrownBy(() -> sensors.list(null, null, null, 0, 0))
        .isInstanceOf(AssetAccessDeniedException.class);
  }

  @Test
  void update_changesTechnicalDataAndRecordsTheOldAndNewValues() {
    Sensor sensor = register(f.anAsset(), "SN-1");
    f.store.history.clear();

    Sensor updated = sensors.update(ADMIN, sensor.id(), 1, "SN-1B", null);

    assertThat(updated.serialNumber()).isEqualTo("SN-1B");
    assertThat(updated.model()).isEqualTo("Model X");
    assertThat(updated.version()).isEqualTo(2);
    assertThat(f.store.history)
        .singleElement()
        .satisfies(
            h -> {
              assertThat(h.action()).isEqualTo("TECHNICAL_DATA_UPDATED");
              assertThat(h.previousValue()).containsEntry("serialNumber", "SN-1");
              assertThat(h.newValue()).containsEntry("serialNumber", "SN-1B");
            });
  }

  @Test
  void update_thatChangesNothingWritesNothing() {
    Sensor sensor = register(f.anAsset(), "SN-1");
    f.store.history.clear();

    Sensor same = sensors.update(ADMIN, sensor.id(), 1, "SN-1", "Model X");

    assertThat(same.version()).isEqualTo(1);
    assertThat(f.store.history).isEmpty();
  }

  @Test
  void update_rejectsAStaleVersionAndATakenSerial() {
    Asset asset = f.anAsset();
    Sensor first = register(asset, "SN-1");
    register(asset, "SN-2");
    sensors.update(ADMIN, first.id(), 1, null, "Model Y");

    assertThatThrownBy(() -> sensors.update(ADMIN, first.id(), 1, null, "Model Z"))
        .isInstanceOf(StaleVersionException.class);
    assertThatThrownBy(() -> sensors.update(ADMIN, first.id(), 2, "SN-2", null))
        .isInstanceOf(AlreadyExistsException.class);
  }

  @Test
  void list_filtersByAssetAndStatus() {
    Asset a = f.anAsset();
    register(a, "SN-1");
    register(a, "SN-2");

    assertThat(sensors.list(ADMIN, a.id(), null, 0, 0).totalElements()).isEqualTo(2);
    assertThat(sensors.list(ADMIN, a.id(), SensorStatus.ACTIVE, 0, 1).items()).hasSize(1);
    assertThat(sensors.list(ADMIN, a.id(), SensorStatus.RETIRED, 0, 0).items()).isEmpty();
    assertThat(sensors.list(ADMIN, UUID.randomUUID(), null, 0, 0).items()).isEmpty();
  }
}
