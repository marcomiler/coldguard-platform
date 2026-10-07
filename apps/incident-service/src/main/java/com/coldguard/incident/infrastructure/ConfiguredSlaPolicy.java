package com.coldguard.incident.infrastructure;

import com.coldguard.incident.domain.Priority;
import com.coldguard.incident.domain.SlaPolicy;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;

/** SLA deadlines read from configuration; every priority must have an acknowledgement target. */
class ConfiguredSlaPolicy implements SlaPolicy {

  private final Map<Priority, IncidentProperties.Target> targets;

  ConfiguredSlaPolicy(Map<Priority, IncidentProperties.Target> targets) {
    for (Priority priority : EnumSet.allOf(Priority.class)) {
      IncidentProperties.Target target = targets.get(priority);
      if (target == null || target.ack() == null) {
        throw new IllegalStateException("Missing coldguard.incident.sla." + priority + ".ack");
      }
    }
    this.targets = Map.copyOf(targets);
  }

  @Override
  public Instant ackDueAt(Priority priority, Instant createdAt) {
    return createdAt.plus(targets.get(priority).ack());
  }

  @Override
  public Optional<Instant> resolveDueAt(Priority priority, Instant createdAt) {
    return Optional.ofNullable(targets.get(priority).resolve()).map(createdAt::plus);
  }
}
