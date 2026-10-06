package com.coldguard.asset.application;

import static com.coldguard.asset.application.ServiceFixture.ADMIN;
import static com.coldguard.asset.application.ServiceFixture.OPERATOR;
import static com.coldguard.asset.application.ServiceFixture.SUPERVISOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.asset.domain.Asset;
import com.coldguard.asset.domain.Criticality;
import com.coldguard.asset.domain.ResourceNotFoundException;
import com.coldguard.asset.domain.Sensor;
import com.coldguard.asset.domain.SensorStatus;
import com.coldguard.commons.security.Actor;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EvaluationContextServiceTest {

  private static final Actor TELEMETRY = Actor.system("telemetry-service");

  private final ServiceFixture f = new ServiceFixture();
  private final SensorService sensors = f.sensors(Duration.ofDays(90));
  private final EvaluationContextService contexts = f.contexts(3);
  private final Asset asset = f.anAsset();

  private Sensor sensor(String serial) {
    return sensors.register(ADMIN, asset.id(), serial, null, "CELSIUS", null, null);
  }

  @Test
  void onlyAnInternalServiceCallerMayReadContexts() {
    Sensor sensor = sensor("SN-1");

    assertThat(contexts.get(TELEMETRY, sensor.id()).sensorId()).isEqualTo(sensor.id());
    for (Actor caller : new Actor[] {null, ADMIN, SUPERVISOR, OPERATOR}) {
      assertThatThrownBy(() -> contexts.get(caller, sensor.id()))
          .isInstanceOf(AssetAccessDeniedException.class);
      assertThatThrownBy(() -> contexts.getMany(caller, List.of(sensor.id())))
          .isInstanceOf(AssetAccessDeniedException.class);
    }
  }

  @Test
  void aContextCarriesTheAssetCriticalityTheStatusAndTheProfile() {
    Sensor sensor = sensor("SN-1");
    f.profiles.upsert(ADMIN, ServiceFixture.draft(sensor.id(), "CELSIUS", null), 0);

    var context = contexts.get(TELEMETRY, sensor.id());

    assertThat(context.assetId()).isEqualTo(asset.id());
    assertThat(context.assetCriticality()).isEqualTo(Criticality.HIGH);
    assertThat(context.status()).isEqualTo(SensorStatus.ACTIVE);
    assertThat(context.profile()).isNotNull();
    assertThat(context.profile().version()).isEqualTo(1);
  }

  @Test
  void withoutAProfileTheReadingsAreNotEvaluable() {
    assertThat(contexts.get(TELEMETRY, sensor("SN-1").id()).profile()).isNull();
  }

  @Test
  void theContextFollowsChangesToTheAssetAndTheSensor() {
    Sensor sensor = sensor("SN-1");
    f.catalog.updateAsset(ADMIN, asset.id(), 1, null, null, Criticality.CRITICAL);
    f.lifecycle(Duration.ofDays(90)).changeStatus(ADMIN, sensor.id(), SensorStatus.INACTIVE, "off");

    var context = contexts.get(TELEMETRY, sensor.id());

    assertThat(context.assetCriticality()).isEqualTo(Criticality.CRITICAL);
    assertThat(context.status()).isEqualTo(SensorStatus.INACTIVE);
  }

  @Test
  void getMany_omitsUnknownSensorsAndRepeatsNothing() {
    Sensor a = sensor("SN-1");
    Sensor b = sensor("SN-2");

    var found = contexts.getMany(TELEMETRY, List.of(a.id(), UUID.randomUUID(), b.id(), a.id()));

    assertThat(found).extracting(c -> c.sensorId()).containsExactlyInAnyOrder(a.id(), b.id());
    assertThat(contexts.getMany(TELEMETRY, List.of())).isEmpty();
  }

  @Test
  void getMany_refusesMoreThanTheConfiguredBatch() {
    List<UUID> four =
        List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

    assertThatThrownBy(() -> contexts.getMany(TELEMETRY, four))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("3");
    assertThat(contexts.getMany(TELEMETRY, four.subList(0, 3))).isEmpty();
  }

  @Test
  void get_unknownSensorIsNotFound() {
    assertThatThrownBy(() -> contexts.get(TELEMETRY, UUID.randomUUID()))
        .isInstanceOfSatisfying(
            ResourceNotFoundException.class,
            e -> assertThat(e.code()).isEqualTo("SENSOR_NOT_FOUND"));
  }
}
