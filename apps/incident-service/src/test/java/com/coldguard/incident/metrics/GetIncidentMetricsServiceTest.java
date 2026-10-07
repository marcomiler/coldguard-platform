package com.coldguard.incident.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.Role;
import com.coldguard.incident.domain.IncidentStatus;
import com.coldguard.incident.domain.Priority;
import com.coldguard.incident.metrics.application.GetIncidentMetricsService;
import com.coldguard.incident.metrics.application.IncidentMetrics;
import com.coldguard.incident.metrics.application.IncidentMetricsRepository;
import com.coldguard.incident.metrics.application.IncidentMetricsRepository.Group;
import com.coldguard.incident.metrics.application.MetricsAccessDeniedException;
import com.coldguard.incident.metrics.application.MetricsQueryLimits;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GetIncidentMetricsServiceTest {

  private static final Actor SUPERVISOR = new Actor("sup", Set.of(Role.OPERATIONS_SUPERVISOR));
  private static final Instant FROM = Instant.parse("2026-10-01T00:00:00Z");
  private static final Instant TO = Instant.parse("2026-10-02T00:00:00Z");

  private List<Group> groups = List.of();
  private final IncidentMetricsRepository repository = (from, to) -> groups;
  private final GetIncidentMetricsService service =
      new GetIncidentMetricsService(repository, new MetricsQueryLimits(Duration.ofDays(93)));

  @Test
  void onlySupervisorsMayQuery() {
    assertThatThrownBy(() -> service.get(null, FROM, TO))
        .isInstanceOf(MetricsAccessDeniedException.class);
    assertThatThrownBy(() -> service.get(new Actor("a", Set.of(Role.AUDITOR)), FROM, TO))
        .isInstanceOf(MetricsAccessDeniedException.class);
  }

  @Test
  void rangeIsRequiredOrderedAndBounded() {
    assertThatThrownBy(() -> service.get(SUPERVISOR, null, TO))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.get(SUPERVISOR, TO, FROM))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> service.get(SUPERVISOR, FROM, FROM.plus(Duration.ofDays(94))))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("range");
  }

  @Test
  void noIncidents_givesZeroCountsAndNoAveragesOrRatios() {
    IncidentMetrics m = service.get(SUPERVISOR, FROM, TO);

    assertThat(m.countByStatus().values()).containsOnly(0L);
    assertThat(m.mttaSeconds()).isEmpty();
    assertThat(m.mttrSeconds()).isEmpty();
    assertThat(m.byPriority().get(Priority.P1).ackComplianceRatio()).isEmpty();
  }

  @Test
  void combinesGroupsIntoCountsAveragesAndComplianceRatios() {
    groups =
        List.of(
            new Group(Priority.P1, IncidentStatus.CLOSED, 1, 1, 1, 1, 60, 1, 1, 1, 600),
            new Group(Priority.P1, IncidentStatus.ACKNOWLEDGED, 2, 2, 1, 2, 60, 0, 0, 0, 0),
            new Group(Priority.P1, IncidentStatus.CREATED, 1, 0, 0, 0, 0, 0, 0, 0, 0),
            // closed but P4 has no resolution target: not evaluable for the resolve ratio
            new Group(Priority.P4, IncidentStatus.CLOSED, 1, 1, 1, 1, 30, 1, 0, 0, 900));

    IncidentMetrics m = service.get(SUPERVISOR, FROM, TO);

    assertThat(m.countByStatus().get(IncidentStatus.CLOSED)).isEqualTo(2);
    assertThat(m.countByStatus().get(IncidentStatus.CREATED)).isEqualTo(1);
    assertThat(m.countByPriority().get(Priority.P1)).isEqualTo(4);
    assertThat(m.mttaSeconds().orElseThrow()).isEqualTo(150.0 / 4);
    assertThat(m.mttrSeconds().orElseThrow()).isEqualTo(1500.0 / 2);
    var p1 = m.byPriority().get(Priority.P1);
    assertThat(p1.ackComplianceRatio().orElseThrow()).isEqualTo(2.0 / 3);
    assertThat(p1.resolveComplianceRatio().orElseThrow()).isEqualTo(1.0);
    assertThat(m.byPriority().get(Priority.P4).resolveComplianceRatio()).isEmpty();
    assertThat(m.byPriority().get(Priority.P4).ackComplianceRatio().orElseThrow()).isEqualTo(1.0);
  }
}
