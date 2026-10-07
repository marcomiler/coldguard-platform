package com.coldguard.incident.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

/** Shared builders for domain and application tests. */
public final class DomainFixtures {

  public static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");

  /** P1 5m/30m, P2 15m/2h, P3 1h/8h, P4 1d/no resolution target. */
  public static final SlaPolicy SLA =
      new SlaPolicy() {
        @Override
        public Instant ackDueAt(Priority priority, Instant createdAt) {
          return createdAt.plus(
              switch (priority) {
                case P1 -> Duration.ofMinutes(5);
                case P2 -> Duration.ofMinutes(15);
                case P3 -> Duration.ofHours(1);
                case P4 -> Duration.ofDays(1);
              });
        }

        @Override
        public Optional<Instant> resolveDueAt(Priority priority, Instant createdAt) {
          return switch (priority) {
            case P1 -> Optional.of(createdAt.plus(Duration.ofMinutes(30)));
            case P2 -> Optional.of(createdAt.plus(Duration.ofHours(2)));
            case P3 -> Optional.of(createdAt.plus(Duration.ofHours(8)));
            case P4 -> Optional.empty();
          };
        }
      };

  private DomainFixtures() {}

  public static Clock clockAt(Instant instant) {
    return Clock.fixed(instant, ZoneOffset.UTC);
  }

  /** An open incident created at {@link #T0} with the given asset criticality and magnitude. */
  public static Incident open(Criticality criticality, Magnitude magnitude, boolean persistent) {
    return Incident.open(
            "00000000-0000-0000-0000-000000000001",
            "asset-1",
            "sensor-1",
            "TEMPERATURE_ABOVE_MAX",
            criticality,
            magnitude,
            persistent,
            "reading-1",
            SLA,
            clockAt(T0))
        .incident();
  }

  public static Incident openMedium() {
    return open(Criticality.MEDIUM, Magnitude.MEDIUM, false);
  }
}
