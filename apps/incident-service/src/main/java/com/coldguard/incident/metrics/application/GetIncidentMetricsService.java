package com.coldguard.incident.metrics.application;

import com.coldguard.commons.security.Actor;
import com.coldguard.commons.security.Role;
import com.coldguard.incident.domain.IncidentStatus;
import com.coldguard.incident.domain.Priority;
import com.coldguard.incident.metrics.application.IncidentMetricsRepository.Group;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Operational metrics (RF-009): a read-only aggregation, so it emits no event. */
@Service
public class GetIncidentMetricsService {

  private final IncidentMetricsRepository repository;
  private final MetricsQueryLimits limits;

  public GetIncidentMetricsService(
      IncidentMetricsRepository repository, MetricsQueryLimits limits) {
    this.repository = repository;
    this.limits = limits;
  }

  /**
   * @throws IllegalArgumentException if the range is missing, inverted or wider than allowed
   */
  @Transactional(readOnly = true)
  public IncidentMetrics get(Actor actor, Instant from, Instant to) {
    if (actor == null || !actor.hasRole(Role.OPERATIONS_SUPERVISOR)) {
      throw new MetricsAccessDeniedException(actor);
    }
    if (from == null || to == null) {
      throw new IllegalArgumentException("from and to are required");
    }
    if (!from.isBefore(to)) {
      throw new IllegalArgumentException("from must be before to");
    }
    if (Duration.between(from, to).compareTo(limits.maxRange()) > 0) {
      throw new IllegalArgumentException("date range must not exceed " + limits.maxRange());
    }
    return combine(repository.aggregate(from, to));
  }

  static IncidentMetrics combine(List<Group> groups) {
    Map<IncidentStatus, Long> byStatus = new EnumMap<>(IncidentStatus.class);
    Map<Priority, Long> byPriority = new EnumMap<>(Priority.class);
    Map<Priority, IncidentMetrics.PriorityMetrics> detail = new EnumMap<>(Priority.class);
    for (IncidentStatus status : IncidentStatus.values()) {
      byStatus.put(status, 0L);
    }
    for (Priority priority : Priority.values()) {
      byPriority.put(priority, 0L);
      detail.put(priority, new IncidentMetrics.PriorityMetrics(0, 0, 0, 0, 0, 0, 0));
    }
    long acknowledged = 0;
    double ackSeconds = 0;
    long closed = 0;
    double closeSeconds = 0;
    for (Group g : groups) {
      byStatus.merge(g.status(), g.total(), Long::sum);
      byPriority.merge(g.priority(), g.total(), Long::sum);
      detail.merge(
          g.priority(),
          new IncidentMetrics.PriorityMetrics(
              g.total(),
              g.acknowledged(),
              g.acknowledgedOnTime(),
              g.ackEvaluable(),
              g.closed(),
              g.closedOnTime(),
              g.closeEvaluable()),
          (a, b) ->
              new IncidentMetrics.PriorityMetrics(
                  a.total() + b.total(),
                  a.acknowledged() + b.acknowledged(),
                  a.acknowledgedOnTime() + b.acknowledgedOnTime(),
                  a.ackEvaluable() + b.ackEvaluable(),
                  a.closed() + b.closed(),
                  a.closedOnTime() + b.closedOnTime(),
                  a.closeEvaluable() + b.closeEvaluable()));
      acknowledged += g.acknowledged();
      ackSeconds += g.acknowledgeSecondsSum();
      closed += g.closed();
      closeSeconds += g.closeSecondsSum();
    }
    return new IncidentMetrics(
        byStatus,
        byPriority,
        acknowledged == 0 ? OptionalDouble.empty() : OptionalDouble.of(ackSeconds / acknowledged),
        closed == 0 ? OptionalDouble.empty() : OptionalDouble.of(closeSeconds / closed),
        detail);
  }
}
