package com.coldguard.telemetry.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.coldguard.commons.messaging.EventActor;
import com.coldguard.commons.messaging.outbox.OutboundEvent;
import com.coldguard.telemetry.domain.SensorContext;
import com.coldguard.telemetry.domain.SensorStatus;
import com.coldguard.telemetry.support.EventContract;
import com.coldguard.telemetry.support.MutableClock;
import com.coldguard.telemetry.support.NoOpTransactionManager;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/** The profile expects a reading every 5 s; with tolerance 1.5 silence counts after 7.5 s. */
class ConnectivityMonitorTest {

  private final IngestFixture f = new IngestFixture();
  private final MutableClock clock = f.clock;
  private int lostMetric;

  private ConnectivityMonitor monitor(int batchSize) {
    return new ConnectivityMonitor(
        f.store.conditionRepository,
        f.events,
        () -> lostMetric++,
        clock,
        new NoOpTransactionManager(),
        1.5,
        batchSize);
  }

  private void report(SensorContext sensor) {
    f.service.ingest(
        IngestFixture.SIMULATOR,
        com.coldguard.telemetry.domain.ReadingSource.SIMULATOR,
        java.util.List.of(IngestFixture.reading(sensor, 5.0, 0)));
  }

  @Test
  void aSilentSensorIsAnnouncedOnceWithoutChangingItsStatus() {
    SensorContext sensor = f.activeSensor();
    report(sensor);
    clock.advance(Duration.ofSeconds(8));
    f.events.published.clear();

    assertThat(monitor(10).check()).isEqualTo(1);
    assertThat(monitor(10).check()).isZero();

    OutboundEvent event = f.events.only();
    assertThat(event.eventType()).isEqualTo("SensorConnectivityLost");
    assertThat(event.routingKey()).isEqualTo("telemetry.sensor-connectivity-lost");
    assertThat(event.aggregateId()).isEqualTo(sensor.sensorId().toString());
    assertThat(event.actor()).isEqualTo(EventActor.system("connectivity-monitor"));
    EventContract.assertConforms(event);
    var condition = f.store.conditions.get(sensor.sensorId());
    assertThat(condition.connectivityLostAt()).isEqualTo(clock.instant());
    assertThat(condition.sensorStatus()).isEqualTo(SensorStatus.ACTIVE);
    assertThat(lostMetric).isEqualTo(1);
  }

  @Test
  void aSensorWithinTheToleranceIsNotFlagged() {
    SensorContext sensor = f.activeSensor();
    report(sensor);
    clock.advance(Duration.ofSeconds(7));
    f.events.published.clear();

    assertThat(monitor(10).check()).isZero();
    assertThat(f.events.published).isEmpty();
  }

  @Test
  void sensorsThatAreNotActiveOrHaveNoIntervalAreNeverFlagged() {
    SensorContext maintenance = f.sensor(SensorStatus.ACTIVE, IngestFixture.PROFILE);
    SensorContext noProfile = f.sensor(SensorStatus.ACTIVE, null);
    report(maintenance);
    report(noProfile);
    f.store.conditions.computeIfPresent(
        maintenance.sensorId(),
        (id, c) -> c.statusChanged(SensorStatus.IN_MAINTENANCE, clock.instant().minusSeconds(1)));
    clock.advance(Duration.ofHours(1));
    f.events.published.clear();

    assertThat(monitor(10).check()).isZero();
    assertThat(f.events.published).isEmpty();
  }

  @Test
  void aReadingAfterTheLossClearsTheFlagAndAFurtherSilenceIsAnnouncedAgain() {
    SensorContext sensor = f.activeSensor();
    report(sensor);
    clock.advance(Duration.ofSeconds(8));
    monitor(10).check();

    report(sensor);
    assertThat(f.store.conditions.get(sensor.sensorId()).connectivityLostAt()).isNull();
    clock.advance(Duration.ofSeconds(8));
    f.events.published.clear();

    assertThat(monitor(10).check()).isEqualTo(1);
    assertThat(f.events.published).hasSize(1);
  }

  @Test
  void manySilentSensorsAreFlaggedInBatches() {
    for (int i = 0; i < 5; i++) {
      report(f.activeSensor());
    }
    clock.advance(Duration.ofSeconds(8));
    f.events.published.clear();

    assertThat(monitor(2).check()).isEqualTo(5);
    assertThat(f.events.published).hasSize(5);
    assertThat(lostMetric).isEqualTo(5);
  }
}
