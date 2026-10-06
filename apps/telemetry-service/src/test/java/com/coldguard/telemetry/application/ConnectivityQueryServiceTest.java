package com.coldguard.telemetry.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.Role;
import com.coldguard.telemetry.domain.ReadingSource;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ConnectivityQueryServiceTest {

  private static final Actor SUPERVISOR = new Actor("s", Set.of(Role.OPERATIONS_SUPERVISOR));

  private final IngestFixture f = new IngestFixture();
  private final ConnectivityQueryService service =
      new ConnectivityQueryService(f.store.conditionRepository, 3, 2);

  private void sensors(int count) {
    for (int i = 0; i < count; i++) {
      f.service.ingest(
          IngestFixture.SIMULATOR,
          ReadingSource.SIMULATOR,
          List.of(IngestFixture.reading(f.activeSensor(), 5.0, 0)));
    }
  }

  @Test
  void pagesThroughTheConditionsWithTheTotal() {
    sensors(3);

    var first = service.list(SUPERVISOR, false, 0, 0);
    var second = service.list(SUPERVISOR, false, 1, 0);

    assertThat(first.items()).hasSize(2);
    assertThat(second.items()).hasSize(1);
    assertThat(first.totalElements()).isEqualTo(3);
    assertThat(first.totalPages()).isEqualTo(2);
  }

  @Test
  void onlyLostKeepsTheSilentOnes() {
    sensors(2);
    var any = f.store.conditions.values().iterator().next();
    f.store.conditions.put(any.sensorId(), any.connectivityLost(Instant.now()));

    var lost = service.list(SUPERVISOR, true, 0, 0);

    assertThat(lost.items()).extracting(c -> c.sensorId()).containsExactly(any.sensorId());
    assertThat(lost.totalElements()).isEqualTo(1);
  }

  @Test
  void rejectsOtherRolesAndInvalidPaging() {
    assertThatThrownBy(() -> service.list(IngestFixture.SIMULATOR, false, 0, 0))
        .isInstanceOf(TelemetryAccessDeniedException.class);
    assertThatThrownBy(() -> service.list(null, false, 0, 0))
        .isInstanceOf(TelemetryAccessDeniedException.class);
    assertThatThrownBy(() -> service.list(SUPERVISOR, false, -1, 0))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.list(SUPERVISOR, false, 0, 4))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
