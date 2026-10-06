package com.coldguard.telemetry.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.asset.grpc.v1.SensorStatus;
import com.coldguard.telemetry.application.AssetUnavailableException;
import com.coldguard.telemetry.domain.Criticality;
import io.grpc.Status;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssetContextClientTest {

  private FakeAsset asset;
  private AssetContextClient client;

  @BeforeEach
  void start() throws Exception {
    asset = new FakeAsset();
    client = new AssetContextClient(asset.stub, Duration.ofSeconds(2));
  }

  @AfterEach
  void stop() throws Exception {
    asset.close();
  }

  @Test
  void aSensorWithAProfileIsMappedToTheDomain() {
    String id = asset.addSensor(SensorStatus.SENSOR_STATUS_ACTIVE, true);

    var context = client.fetch(List.of(UUID.fromString(id))).get(0);

    assertThat(context.sensorId()).isEqualTo(UUID.fromString(id));
    assertThat(context.assetCriticality()).isEqualTo(Criticality.HIGH);
    assertThat(context.status()).isEqualTo(com.coldguard.telemetry.domain.SensorStatus.ACTIVE);
    var profile = context.profile();
    assertThat(profile.minTemperature()).isEqualByComparingTo("2");
    assertThat(profile.maxTemperature()).isEqualByComparingTo("8");
    assertThat(profile.unit()).isEqualTo("CELSIUS");
    assertThat(profile.mediumFrom()).isEqualByComparingTo("1");
    assertThat(profile.highFrom()).isEqualByComparingTo("3");
    assertThat(profile.criticalFrom()).isEqualByComparingTo("6");
    assertThat(profile.minConsecutiveBreaches()).isEqualTo(3);
    assertThat(profile.persistenceWindow()).isEqualTo(Duration.ofMinutes(5));
    assertThat(profile.expectedInterval()).isEqualTo(Duration.ofSeconds(5));
  }

  @Test
  void everyStatusIsMapped() {
    for (SensorStatus status :
        new SensorStatus[] {
          SensorStatus.SENSOR_STATUS_ACTIVE,
          SensorStatus.SENSOR_STATUS_IN_MAINTENANCE,
          SensorStatus.SENSOR_STATUS_INACTIVE,
          SensorStatus.SENSOR_STATUS_RETIRED
        }) {
      String id = asset.addSensor(status, true);
      assertThat(client.fetch(List.of(UUID.fromString(id))).get(0).status().name())
          .isEqualTo(status.name().substring("SENSOR_STATUS_".length()));
    }
  }

  @Test
  void aSensorWithoutAProfileHasNoProfile() {
    String id = asset.addSensor(SensorStatus.SENSOR_STATUS_ACTIVE, false);

    assertThat(client.fetch(List.of(UUID.fromString(id))).get(0).profile()).isNull();
  }

  @Test
  void aSensorThatDoesNotExistIsAbsent() {
    String known = asset.addSensor(SensorStatus.SENSOR_STATUS_ACTIVE, true);

    var found = client.fetch(List.of(UUID.fromString(known), UUID.randomUUID()));

    assertThat(found).hasSize(1);
    assertThat(found.get(0).sensorId()).isEqualTo(UUID.fromString(known));
  }

  @Test
  void manySensorsAreAskedInCallsOfAtMostWhatAssetAccepts() {
    List<UUID> ids = new ArrayList<>();
    for (int i = 0; i < 1201; i++) {
      ids.add(UUID.randomUUID());
    }

    assertThat(client.fetch(ids)).isEmpty();

    assertThat(asset.calls).extracting(List::size).containsExactly(500, 500, 201);
  }

  @Test
  void nothingIsAskedWhenThereAreNoSensors() {
    assertThat(client.fetch(List.of())).isEmpty();
    assertThat(asset.calls).isEmpty();
  }

  @Test
  void anyFailureOfAssetMeansItIsUnavailable() {
    String id = asset.addSensor(SensorStatus.SENSOR_STATUS_ACTIVE, true);
    for (Status status :
        new Status[] {
          Status.UNAVAILABLE, Status.INTERNAL, Status.PERMISSION_DENIED, Status.UNKNOWN
        }) {
      asset.failure = status;
      assertThatThrownBy(() -> client.fetch(List.of(UUID.fromString(id))))
          .as(status.getCode().name())
          .isInstanceOf(AssetUnavailableException.class);
    }
  }

  @Test
  void aSlowAssetIsCutOffAtTheDeadline() throws Exception {
    asset.neverAnswers = true;
    AssetContextClient impatient = new AssetContextClient(asset.stub, Duration.ofMillis(300));
    long started = System.nanoTime();

    assertThatThrownBy(() -> impatient.fetch(List.of(UUID.randomUUID())))
        .isInstanceOf(AssetUnavailableException.class);

    assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
  }
}
