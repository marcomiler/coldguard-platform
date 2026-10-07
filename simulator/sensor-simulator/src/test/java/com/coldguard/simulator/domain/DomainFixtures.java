package com.coldguard.simulator.domain;

import java.time.Duration;
import java.time.Instant;

final class DomainFixtures {

  static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");
  static final String SENSOR = "00000000-0000-0000-0000-000000000001";

  private DomainFixtures() {}

  static SensorSpec spec(Scenario scenario) {
    return new SensorSpec(
        SENSOR,
        "CELSIUS",
        Duration.ofSeconds(5),
        4.0,
        0.3,
        scenario,
        scenario == Scenario.NOMINAL || scenario == Scenario.CONNECTIVITY_LOSS ? null : 11.5,
        Duration.ofSeconds(20),
        scenario == Scenario.RECOVERY ? 2 : 1,
        scenario == Scenario.CONNECTIVITY_LOSS ? Duration.ofSeconds(60) : Duration.ZERO);
  }
}
