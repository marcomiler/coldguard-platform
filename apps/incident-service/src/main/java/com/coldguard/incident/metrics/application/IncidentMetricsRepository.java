package com.coldguard.incident.metrics.application;

import com.coldguard.incident.domain.IncidentStatus;
import com.coldguard.incident.domain.Priority;
import java.time.Instant;
import java.util.List;

public interface IncidentMetricsRepository {

  /** One aggregate of the incidents sharing a priority and status. */
  record Group(
      Priority priority,
      IncidentStatus status,
      long total,
      long acknowledged,
      long acknowledgedOnTime,
      long ackEvaluable,
      double acknowledgeSecondsSum,
      long closed,
      long closedOnTime,
      long closeEvaluable,
      double closeSecondsSum) {}

  /** Aggregates incidents created in {@code [from, to)} in a single query. */
  List<Group> aggregate(Instant from, Instant to);
}
