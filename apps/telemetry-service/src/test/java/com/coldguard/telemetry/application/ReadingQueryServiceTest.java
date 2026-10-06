package com.coldguard.telemetry.application;

import static com.coldguard.telemetry.application.IngestFixture.ADMIN;
import static com.coldguard.telemetry.application.IngestFixture.NOW;
import static com.coldguard.telemetry.application.IngestFixture.SIMULATOR;
import static com.coldguard.telemetry.application.IngestFixture.reading;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.Role;
import com.coldguard.telemetry.domain.ReadingSource;
import com.coldguard.telemetry.domain.SensorContext;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ReadingQueryServiceTest {

  private static final Actor SUPERVISOR = new Actor("sup-1", Set.of(Role.OPERATIONS_SUPERVISOR));

  private final IngestFixture f = new IngestFixture();
  private final ReadingQueryService queries =
      new ReadingQueryService(f.store.readingRepository, Duration.ofDays(7), 100, 20);
  private final SensorContext sensor = f.activeSensor();

  private void ingest(int count) {
    List<IncomingReading> batch = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      batch.add(reading(sensor, 5.0, 1000L - i));
      if (batch.size() == 5) {
        f.service.ingest(SIMULATOR, ReadingSource.SIMULATOR, batch);
        batch = new ArrayList<>();
      }
    }
    if (!batch.isEmpty()) {
      f.service.ingest(SIMULATOR, ReadingSource.SIMULATOR, batch);
    }
  }

  @Test
  void onlyAdministratorsAndSupervisorsMayRead() {
    ingest(1);
    var from = NOW.minusSeconds(3600);

    assertThat(queries.list(ADMIN, sensor.sensorId(), from, NOW, "", 0).readings()).hasSize(1);
    assertThat(queries.list(SUPERVISOR, sensor.sensorId(), from, NOW, "", 0).readings()).hasSize(1);
    for (Actor denied : new Actor[] {null, SIMULATOR, new Actor("op", Set.of(Role.OPERATOR))}) {
      assertThatThrownBy(() -> queries.list(denied, sensor.sensorId(), from, NOW, "", 0))
          .isInstanceOf(TelemetryAccessDeniedException.class);
    }
  }

  @Test
  void aRangeIsRequiredOrderedAndBounded() {
    assertThatThrownBy(() -> queries.list(ADMIN, sensor.sensorId(), null, NOW, "", 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> queries.list(ADMIN, sensor.sensorId(), NOW.minusSeconds(60), null, "", 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> queries.list(ADMIN, sensor.sensorId(), NOW, NOW, "", 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> queries.list(ADMIN, sensor.sensorId(), NOW, NOW.minusSeconds(1), "", 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> queries.list(ADMIN, sensor.sensorId(), NOW.minus(Duration.ofDays(8)), NOW, "", 0))
        .isInstanceOf(RangeTooWideException.class);
    assertThat(queries.list(ADMIN, sensor.sensorId(), NOW.minus(Duration.ofDays(7)), NOW, "", 0))
        .isNotNull();
  }

  @Test
  void aCursorWalksTheWholeRangeNewestFirstWithoutRepeatingAnything() {
    ingest(23);
    var from = NOW.minusSeconds(3600);

    List<StoredReading> seen = new ArrayList<>();
    String cursor = "";
    int pages = 0;
    do {
      var page = queries.list(ADMIN, sensor.sensorId(), from, NOW, cursor, 10);
      seen.addAll(page.readings());
      cursor = page.nextCursor();
      pages++;
      assertThat(page.hasMore()).isEqualTo(!cursor.isEmpty());
    } while (!cursor.isEmpty());

    assertThat(seen).hasSize(23);
    assertThat(pages).isEqualTo(3);
    Set<UUID> ids = new HashSet<>();
    seen.forEach(r -> assertThat(ids.add(r.id())).isTrue());
    for (int i = 1; i < seen.size(); i++) {
      assertThat(seen.get(i).recordedAt()).isBeforeOrEqualTo(seen.get(i - 1).recordedAt());
    }
  }

  @Test
  void onlyReadingsInsideTheRangeAreReturnedAndTheEndIsExclusive() {
    ingest(5);
    {
      var all =
          queries.list(ADMIN, sensor.sensorId(), NOW.minusSeconds(2000), NOW, "", 0).readings();
      assertThat(all).hasSize(5);
      var newest = all.get(0).recordedAt();
      assertThat(
              queries
                  .list(ADMIN, sensor.sensorId(), NOW.minusSeconds(2000), newest, "", 0)
                  .readings())
          .hasSize(4);
      assertThat(queries.list(ADMIN, sensor.sensorId(), newest, NOW, "", 0).readings()).hasSize(1);
    }
    assertThat(
            queries.list(ADMIN, UUID.randomUUID(), NOW.minusSeconds(2000), NOW, "", 0).readings())
        .isEmpty();
  }

  @Test
  void theSizeDefaultsAndIsBounded() {
    ingest(25);
    var from = NOW.minusSeconds(3600);

    assertThat(queries.list(ADMIN, sensor.sensorId(), from, NOW, "", 0).readings()).hasSize(20);
    assertThatThrownBy(() -> queries.list(ADMIN, sensor.sensorId(), from, NOW, "", 101))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> queries.list(ADMIN, sensor.sensorId(), from, NOW, "", -1))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> queries.list(ADMIN, sensor.sensorId(), from, NOW, "garbage!!", 10))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("cursor");
  }
}
