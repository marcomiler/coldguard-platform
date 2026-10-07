package com.coldguard.incident.metrics.application;

import com.coldguard.incident.domain.IncidentStatus;
import com.coldguard.incident.domain.Priority;
import java.util.Map;
import java.util.OptionalDouble;

/** Measured values for incidents created in a range; no verdict against aggregate targets. */
public record IncidentMetrics(
    Map<IncidentStatus, Long> countByStatus,
    Map<Priority, Long> countByPriority,
    OptionalDouble mttaSeconds,
    OptionalDouble mttrSeconds,
    Map<Priority, PriorityMetrics> byPriority) {

  /**
   * {@code ackEvaluable} / {@code closeEvaluable} count the acknowledged (closed) incidents that
   * had a deadline, so the ratios ignore incidents without a target (P4 resolution, legacy rows).
   */
  public record PriorityMetrics(
      long total,
      long acknowledged,
      long acknowledgedOnTime,
      long ackEvaluable,
      long closed,
      long closedOnTime,
      long closeEvaluable) {

    public OptionalDouble ackComplianceRatio() {
      return ratio(acknowledgedOnTime, ackEvaluable);
    }

    public OptionalDouble resolveComplianceRatio() {
      return ratio(closedOnTime, closeEvaluable);
    }

    private static OptionalDouble ratio(long part, long whole) {
      return whole == 0 ? OptionalDouble.empty() : OptionalDouble.of((double) part / whole);
    }
  }
}
