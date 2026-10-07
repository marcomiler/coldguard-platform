package com.coldguard.incident.application;

import com.coldguard.incident.domain.IncidentStatus;
import com.coldguard.incident.domain.Priority;
import java.time.Instant;
import java.util.Set;

/** Optional filters; empty sets and nulls mean "any". */
public record IncidentSearch(
    Set<IncidentStatus> statuses,
    Set<Priority> priorities,
    String assetId,
    String sensorId,
    Instant createdFrom,
    Instant createdTo) {

  public IncidentSearch {
    statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
    priorities = priorities == null ? Set.of() : Set.copyOf(priorities);
  }
}
