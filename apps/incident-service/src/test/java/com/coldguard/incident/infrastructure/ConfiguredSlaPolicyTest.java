package com.coldguard.incident.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.incident.domain.Priority;
import com.coldguard.incident.infrastructure.IncidentProperties.Target;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConfiguredSlaPolicyTest {

  private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

  private static final Map<Priority, Target> TARGETS =
      Map.of(
          Priority.P1, new Target(Duration.ofMinutes(5), Duration.ofMinutes(30)),
          Priority.P2, new Target(Duration.ofMinutes(15), Duration.ofHours(2)),
          Priority.P3, new Target(Duration.ofHours(1), Duration.ofHours(8)),
          Priority.P4, new Target(Duration.ofDays(1), null));

  @Test
  void deadlinesAreMeasuredFromCreation() {
    ConfiguredSlaPolicy policy = new ConfiguredSlaPolicy(TARGETS);

    assertThat(policy.ackDueAt(Priority.P2, T0)).isEqualTo(T0.plus(Duration.ofMinutes(15)));
    assertThat(policy.resolveDueAt(Priority.P2, T0)).contains(T0.plus(Duration.ofHours(2)));
  }

  @Test
  void p4HasNoResolutionTarget() {
    assertThat(new ConfiguredSlaPolicy(TARGETS).resolveDueAt(Priority.P4, T0)).isEmpty();
  }

  @Test
  void missingAcknowledgementTarget_failsFastAtStartup() {
    assertThatThrownBy(() -> new ConfiguredSlaPolicy(Map.of(Priority.P1, TARGETS.get(Priority.P1))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("coldguard.incident.sla.");
  }
}
