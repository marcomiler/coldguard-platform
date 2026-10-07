package com.coldguard.incident.domain;

import java.time.Instant;
import java.util.Optional;

/** Response-time targets per priority, measured from the moment the incident is created. */
public interface SlaPolicy {

  Instant ackDueAt(Priority priority, Instant createdAt);

  /** Empty when the priority has no resolution target. */
  Optional<Instant> resolveDueAt(Priority priority, Instant createdAt);
}
