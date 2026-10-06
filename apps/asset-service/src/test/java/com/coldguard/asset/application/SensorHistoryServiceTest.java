package com.coldguard.asset.application;

import static com.coldguard.asset.application.ServiceFixture.ADMIN;
import static com.coldguard.asset.application.ServiceFixture.OPERATOR;
import static com.coldguard.asset.application.ServiceFixture.SUPERVISOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.asset.domain.ResourceNotFoundException;
import com.coldguard.asset.domain.Sensor;
import com.coldguard.asset.domain.SensorStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SensorHistoryServiceTest {

  private final ServiceFixture f = new ServiceFixture();
  private final SensorService sensors = f.sensors(Duration.ofDays(90));
  private final SensorLifecycleService lifecycle = f.lifecycle(Duration.ofDays(90));
  private final SensorHistoryService history = f.history();

  private Sensor sensorWithManyEntries() {
    Sensor sensor =
        sensors.register(
            ADMIN,
            f.anAsset().id(),
            "SN-1",
            "A",
            "CELSIUS",
            null,
            ServiceFixture.draft(UUID.randomUUID(), "CELSIUS", null));
    f.clock.advance(Duration.ofMinutes(1));
    sensors.update(ADMIN, sensor.id(), 1, null, "B");
    f.clock.advance(Duration.ofMinutes(1));
    lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.INACTIVE, "off");
    f.clock.advance(Duration.ofMinutes(1));
    lifecycle.changeStatus(ADMIN, sensor.id(), SensorStatus.IN_MAINTENANCE, "inspect");
    f.clock.advance(Duration.ofMinutes(1));
    lifecycle.retire(ADMIN, sensor.id(), "end");
    return sensor;
  }

  @Test
  void onlyAdministratorsMayReadTheHistory() {
    Sensor sensor = sensorWithManyEntries();

    assertThat(history.history(ADMIN, sensor.id(), "", 0).items()).isNotEmpty();
    for (var caller : new com.coldguard.commons.security.Actor[] {null, SUPERVISOR, OPERATOR}) {
      assertThatThrownBy(() -> history.history(caller, sensor.id(), "", 0))
          .isInstanceOf(AssetAccessDeniedException.class);
    }
  }

  @Test
  void entriesComeNewestFirstAndEveryChangeIsThere() {
    Sensor sensor = sensorWithManyEntries();

    var page = history.history(ADMIN, sensor.id(), "", 100);

    assertThat(page.hasMore()).isFalse();
    assertThat(page.nextCursor()).isEmpty();
    assertThat(page.items().stream().map(SensorHistoryEntry::action))
        .startsWith("RETIRED", "STATUS_CHANGED", "STATUS_CHANGED", "TECHNICAL_DATA_UPDATED")
        .contains("REGISTERED", "PROFILE_UPDATED");
    assertThat(page.items())
        .allSatisfy(
            e -> {
              assertThat(e.actorId()).isEqualTo("admin-1");
              assertThat(e.occurredAt()).isNotNull();
            });
  }

  @Test
  void aCursorWalksTheWholeHistoryOncePageAfterPage() {
    Sensor sensor = sensorWithManyEntries();
    int total = f.store.history.size();

    List<SensorHistoryEntry> seen = new ArrayList<>();
    String cursor = "";
    int pages = 0;
    do {
      var page = history.history(ADMIN, sensor.id(), cursor, 2);
      assertThat(page.items().size()).isLessThanOrEqualTo(2);
      seen.addAll(page.items());
      cursor = page.nextCursor();
      pages++;
      assertThat(page.hasMore()).isEqualTo(!cursor.isEmpty());
    } while (!cursor.isEmpty());

    assertThat(seen).hasSize(total);
    Set<UUID> ids = new HashSet<>();
    seen.forEach(e -> assertThat(ids.add(e.id())).as("no entry twice").isTrue());
    assertThat(pages).isEqualTo((total + 1) / 2);
    for (int i = 1; i < seen.size(); i++) {
      assertThat(seen.get(i).occurredAt()).isBeforeOrEqualTo(seen.get(i - 1).occurredAt());
    }
  }

  @Test
  void aMalformedCursorAndOutOfRangeSizesAreInvalid() {
    Sensor sensor = sensorWithManyEntries();

    for (String bad : new String[] {"not-base64!!", "YWJj", "bm9wZXxub3BlCg"}) {
      assertThatThrownBy(() -> history.history(ADMIN, sensor.id(), bad, 10))
          .as("cursor %s", bad)
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("cursor");
    }
    assertThatThrownBy(() -> history.history(ADMIN, sensor.id(), "", 101))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> history.history(ADMIN, sensor.id(), "", -1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void anUnknownSensorIsNotFound() {
    assertThatThrownBy(() -> history.history(ADMIN, UUID.randomUUID(), "", 10))
        .isInstanceOf(ResourceNotFoundException.class);
  }

  @Test
  void aRetiredSensorsHistoryStaysReadable() {
    Sensor sensor = sensorWithManyEntries();

    assertThat(history.history(ADMIN, sensor.id(), "", 100).items().get(0).action())
        .isEqualTo("RETIRED");
  }
}
