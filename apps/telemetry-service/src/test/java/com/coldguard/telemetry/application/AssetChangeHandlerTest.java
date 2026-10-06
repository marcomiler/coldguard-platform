package com.coldguard.telemetry.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.coldguard.telemetry.domain.SensorCondition;
import com.coldguard.telemetry.domain.SensorContext;
import com.coldguard.telemetry.domain.SensorStatus;
import com.coldguard.telemetry.support.InMemoryTelemetryStore;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AssetChangeHandlerTest {

  private static final Instant T0 = Instant.parse("2026-10-06T12:00:00Z");

  private final InMemoryTelemetryStore store = new InMemoryTelemetryStore();
  private final List<String> evictions = new ArrayList<>();
  private final AssetChangeHandler handler =
      new AssetChangeHandler(
          store.conditionRepository,
          new SensorContextEviction() {
            @Override
            public void evict(UUID sensorId) {
              evictions.add("sensor:" + sensorId);
            }

            @Override
            public void evictAll() {
              evictions.add("all");
            }
          });

  private SensorContext sensor() {
    SensorContext context =
        new SensorContext(
            UUID.randomUUID(),
            UUID.randomUUID(),
            com.coldguard.telemetry.domain.Criticality.HIGH,
            SensorStatus.ACTIVE,
            IngestFixture.PROFILE);
    store.conditionRepository.lockOrCreate(context, T0);
    return context;
  }

  private SensorCondition condition(SensorContext sensor) {
    return store.conditions.get(sensor.sensorId());
  }

  @Test
  void aStatusChangeForgetsTheCachedContextAndUpdatesTheCondition() {
    SensorContext sensor = sensor();

    handler.sensorStatusChanged(sensor.sensorId(), SensorStatus.IN_MAINTENANCE, T0.plusSeconds(10));

    assertThat(evictions).containsExactly("sensor:" + sensor.sensorId());
    assertThat(condition(sensor).sensorStatus()).isEqualTo(SensorStatus.IN_MAINTENANCE);
    assertThat(condition(sensor).assetStateAt()).isEqualTo(T0.plusSeconds(10));
  }

  @Test
  void aSensorThatNeverReportedOnlyHasItsCacheForgotten() {
    UUID unknown = UUID.randomUUID();

    handler.sensorStatusChanged(unknown, SensorStatus.INACTIVE, T0);

    assertThat(evictions).containsExactly("sensor:" + unknown);
    assertThat(store.conditions).isEmpty();
  }

  @Test
  void anOlderChangeNeverUndoesANewerOne() {
    SensorContext sensor = sensor();
    handler.sensorStatusChanged(sensor.sensorId(), SensorStatus.INACTIVE, T0.plusSeconds(20));

    handler.sensorStatusChanged(sensor.sensorId(), SensorStatus.ACTIVE, T0.plusSeconds(10));
    handler.sensorStatusChanged(sensor.sensorId(), SensorStatus.ACTIVE, T0.plusSeconds(20));

    assertThat(condition(sensor).sensorStatus()).isEqualTo(SensorStatus.INACTIVE);
    assertThat(condition(sensor).assetStateAt()).isEqualTo(T0.plusSeconds(20));
  }

  @Test
  void aRetiredSensorIsNoLongerMonitoredAndItsLossFlagIsCleared() {
    SensorContext sensor = sensor();
    store.conditions.put(sensor.sensorId(), condition(sensor).connectivityLost(T0.plusSeconds(5)));

    handler.sensorStatusChanged(sensor.sensorId(), SensorStatus.RETIRED, T0.plusSeconds(10));

    assertThat(condition(sensor).sensorStatus()).isEqualTo(SensorStatus.RETIRED);
    assertThat(condition(sensor).connectivityLostAt()).isNull();
  }

  @Test
  void aSensorBackInServiceGetsAFullIntervalBeforeItCanBeFlagged() {
    SensorContext sensor = sensor();
    handler.sensorStatusChanged(sensor.sensorId(), SensorStatus.IN_MAINTENANCE, T0.plusSeconds(10));
    Instant back = T0.plus(Duration.ofHours(2));

    handler.sensorStatusChanged(sensor.sensorId(), SensorStatus.ACTIVE, back);

    assertThat(condition(sensor).sensorStatus()).isEqualTo(SensorStatus.ACTIVE);
    assertThat(condition(sensor).lastReadingAt()).isEqualTo(back);
    assertThat(store.conditionRepository.lockOverdue(back.plusSeconds(5), 1.5, 10)).isEmpty();
    assertThat(store.conditionRepository.lockOverdue(back.plusSeconds(8), 1.5, 10)).hasSize(1);
  }

  @Test
  void aReassignmentMovesTheConditionToTheNewAsset() {
    SensorContext sensor = sensor();
    UUID target = UUID.randomUUID();

    handler.sensorReassigned(sensor.sensorId(), target, T0.plusSeconds(1));

    assertThat(condition(sensor).assetId()).isEqualTo(target);
    assertThat(evictions).containsExactly("sensor:" + sensor.sensorId());
  }

  @Test
  void aNewReportingIntervalReplacesTheExpectedOne() {
    SensorContext sensor = sensor();

    handler.expectedIntervalChanged(sensor.sensorId(), 60, T0.plusSeconds(1));

    assertThat(condition(sensor).expectedIntervalSeconds()).isEqualTo(60);
  }

  @Test
  void anAssetUpdateForgetsEveryCachedContext() {
    handler.assetUpdated();

    assertThat(evictions).containsExactly("all");
  }

  @Test
  void aReplayedChangeLeavesTheSameState() {
    SensorContext sensor = sensor();

    handler.sensorStatusChanged(sensor.sensorId(), SensorStatus.INACTIVE, T0.plusSeconds(10));
    SensorCondition once = condition(sensor);
    handler.sensorStatusChanged(sensor.sensorId(), SensorStatus.INACTIVE, T0.plusSeconds(10));

    assertThat(condition(sensor)).isEqualTo(once);
  }
}
